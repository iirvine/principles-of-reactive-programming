package week1

import generators._

object treegenerator {
  val integers = new Generator[Int] {
  	def generate = scala.util.Random.nextInt()
  }                                               //> integers  : generators.Generator[Int] = week1.treegenerator$$anonfun$main$1$
                                                  //| $anon$1@74b23210
  
  val booleans = integers.map(_ >= 0)             //> booleans  : generators.Generator[Boolean] = generators.Generator$$anon$1@764
                                                  //| 97934
  
  def leafs: Generator[Leaf] = for {
  	x <- integers
  } yield Leaf(x)                                 //> leafs: => generators.Generator[week1.Leaf]
                                                  
  def inners: Generator[Inner] = for {
  	l <- trees
  	r <- trees
  } yield Inner(l, r)                             //> inners: => generators.Generator[week1.Inner]
 	
 	def trees: Generator[Tree] = for {
 		isLeaf <- booleans
 		tree <- if (isLeaf) leafs else inners
 	} yield tree                              //> trees: => generators.Generator[week1.Tree]
 	
 	trees.generate                            //> res0: week1.Tree = Leaf(-275150351)
                                                    
}