package com.example.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.example.actors.NodeSearch.{Event, PrintGraph, SendOptimalSolution}
import com.example.actors.SolutionNode.SolutionEvent
import com.example.{Solution, TreeNode}
import com.example.solver.SolverScalaWrapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

class NodeSearch (node: TreeNode, child_refs: Map[ActorRef[Node.Event], List[Int]], parent_solution_node: ActorRef[SolutionEvent], context: ActorContext[Event]) {

  val COLOR_COST_MAPPING = Map(
    ("red" -> 5),
    ("blue" -> 4),
    ("yellow" -> 1),
    ("green" -> 2),
    ("white" -> 0),
    ("black" -> 0),
    ("orange" -> 2)
  )

  //For now lets just say we want the most amount of color 'red'
  def calcCost(color_mapping: Map[Int, String]): Int = {
    var cost: Int = 0
    color_mapping.foreach {
      case (_, color) =>
        cost += COLOR_COST_MAPPING.getOrElse(color, 0)
    }
    cost
  }

  def mainLoop(context: ActorContext[NodeSearch.Event], best_solution: Option[Map[Int, String]], best_score: Int, solutions: List[Map[Int, String]]): Behavior[Event] = {
    if (solutions.nonEmpty) {
      val solution = solutions.head
      val new_node = node.updateNodes(solution)
      val current_solution = new_node.full_graph_mapping
      val solution_id = node.id.toString + "_" + solutions.size

      val solution_actor = context.spawn(
        SolutionNode(Solution(solution_id, new_node, current_solution, 0), new_node.tree_children, context.self, child_refs),
        solution_id
      )
      Behaviors.receive { (context, message) =>
        message match {
          case SendOptimalSolution(received_solution: Option[Map[Int, String]]) =>
            val new_solution: Map[Int, String] = if (received_solution.isEmpty) Map() else received_solution.get
            val cost = this.calcCost(new_solution)
            if (cost >= best_score) {
              mainLoop(context, Option(new_solution), cost, solutions.tail)
            } else {
              mainLoop(context, best_solution, best_score, solutions.tail)
            }
          case PrintGraph() =>
            context.log.info(
              "Tree Node {}, has graph nodes: {}, parent: {}, children: {}, path:  {}",
              node.id,
              node.graph_variables,
              node.parent,
              node.tree_children,
              context.self.path,
            )
            Behaviors.same
          case _ =>
            context.log.error("Unexpected message: " + message)
            Behaviors.stopped
        }
      }
    } else {
      context.log.info("Found local best solution: {}, score: {}, stopping...", best_solution, best_score)
      if (best_solution.isEmpty) {
        parent_solution_node ! SolutionNode.SendSolution(Map(), 0)
      } else {
        parent_solution_node ! SolutionNode.SendSolution(best_solution.get, best_score)
      }
      Behaviors.stopped
    }
  }

  def receiveNodeRef(solutions: List[Map[Int, String]]): Behavior[Event] = {
    context.log.info("Node search ready to receive for node: " + node.id.toString)
    mainLoop(context, None, 0, solutions)
  }
}

object NodeSearch {
  sealed trait Event extends SolutionEvent//Scalas enum
  final case class PrintGraph() extends Event
  final case class SendOptimalSolution(@JsonDeserialize(keyAs = classOf[Int]) solution: Option[Map[Int, String]]) extends Event

  def apply(node: TreeNode, child_refs: Map[ActorRef[Node.Event], List[Int]], parent_node: ActorRef[Node.Event], parent_solution_node: ActorRef[SolutionEvent]): Behavior[Event] = Behaviors.setup { context =>
    val solutions = SolverScalaWrapper.calcSolutions(node)
    context.log.info("For variables " + node.graph_variables + " Solution: {}", solutions)

    val node_search = new NodeSearch(node, child_refs, parent_solution_node, context)

    if(solutions.isEmpty) {
      context.log.info("No solutions, stopping")
      parent_solution_node ! SolutionNode.SendSolution(Map(), 0)
      Behaviors.stopped
    } else {
      node_search.receiveNodeRef(solutions)
    }
  }
}
