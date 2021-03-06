package com.example.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.example.actors.SolutionNode.{SendSolution, SolutionEvent}
import com.example.{CborSerializable, Solution}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

class SolutionNode(val solution: Solution, val tree_node_children_ids: List[Int], val parent_node: ActorRef[NodeSearch.Event], val context: ActorContext[SolutionEvent]) {

  def sendSolution(solution: Solution, actorRef: ActorRef[Node.Event], intersection_variables: List[Int]): Unit = {
    //context.log.info("Trying to send solution " + solution.id + " , to actor " + actorRef)
    actorRef ! Node.ReceiveSolution(getSpecificMapping(solution.bareColorMapping(), intersection_variables), context.self)
  }

  def receive(final_solution: Solution, optimal_solutions: List[(Map[Int, String], Int)]): Behavior[SolutionEvent] = {
    if (optimal_solutions.size == tree_node_children_ids.size) {
      val new_final_solution = final_solution.aggregateSolution(optimal_solutions)
//      context.log.info("Done aggregating, sending optimal solution {}", new_final_solution.bareColorMapping()) //Verbose version
      parent_node ! NodeSearch.SendOptimalSolution(Option(new_final_solution.bareColorMapping()))
      Behaviors.stopped
    } else {
      Behaviors.receive { (context, message) =>
        message match {
          case SendSolution(optimal_solution: Map[Int, String], score: Int) =>
            //context.log.info("Received a solution: {} {}", optimal_solution, score)
            if (optimal_solution.isEmpty) {
              parent_node ! NodeSearch.SendOptimalSolution(None)
              Behaviors.stopped
            } else {
              receive(final_solution, optimal_solutions :+ (optimal_solution, score))
            }
          case _ =>
            context.log.error("Unexpected message: " + message)
            Behaviors.stopped
        }
      }
    }
  }

  //Returns subset of full solution mapping (so to only send the values that are intersecting)
  def getSpecificMapping(solution: Map[Int, String], intersection_variables: List[Int]): Map[Int, String] = {
    val map: Map[Int, String] = intersection_variables.map(variable_id =>
      (variable_id -> solution(variable_id))
    ).toMap
    map
  }
}

object SolutionNode {
  trait SolutionEvent extends CborSerializable//Scalas enum
  final case class SendSolution(@JsonDeserialize(keyAs = classOf[Int]) color_mapping: Map[Int, String], score: Int) extends SolutionEvent

  def apply(
             solution: Solution,
             tree_node_children_ids: List[Int],
             parent_ref: ActorRef[NodeSearch.Event],
             child_refs: Map[ActorRef[Node.Event], List[Int]]
           ): Behavior[SolutionEvent] = Behaviors.setup { context =>
    val node = new SolutionNode(solution, tree_node_children_ids, parent_ref, context)
    child_refs.foreach{ case (child_ref: ActorRef[Node.Event], mapping: List[Int]) =>
      node.sendSolution(solution, child_ref, mapping)
    }
    node.receive(solution, List())
  }

}