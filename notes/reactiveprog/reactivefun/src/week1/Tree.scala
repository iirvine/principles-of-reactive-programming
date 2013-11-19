package week1

trait Tree

case class Inner(left: Tree, Right: Tree) extends Tree

case class Leaf(x: Int) extends Tree