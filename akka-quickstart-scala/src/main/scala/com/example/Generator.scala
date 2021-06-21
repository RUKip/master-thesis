package com.example

import scala.annotation.tailrec

object Generator extends App {

  val threshold :Int = 3
  val amount_of_trees = 20

  Range(1, 20).foreach( id => {
    createTree(id)
  })

  def createTreeOld(id: Int): Unit = {
    println("Trying to generate...")
    val base = InitializationHelper.createTreeStructure(3, 4, 1, 1, 1, 10)
    if (base.size < threshold) {
      createTree(id)
    } else {
      println("Created base: " + base.toMap)
      val file_name = "generated_trees/" + id + "_generated_tree.json"
      InitializationHelper.storeTree(base, file_name)
      println("Finished")
      println("***************")
    }
  }

  @tailrec
  def createTree(id: Int): Unit = {
    println("Trying to generate...")
    val base = InitializationHelper.init(4, 7, 5)
    if (base.nonEmpty) {
      if (base.size < threshold) {
        createTree(id)
      } else {
        println("Created base: " + base.toMap)
        val file_name = "generated_trees/" + id + "_generated_tree.json"
        InitializationHelper.storeTree(base, file_name)
        println("Finished")
        println("***************")
      }
    }
  }
}
