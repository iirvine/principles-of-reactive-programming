package week1

import generators._

object treegenerator {
  val integers = new Generator[Int] {
  	def generate = scala.util.Random.nextInt()
  }                                               //> integers  : generators.Generator[Int] = week1.treegenerator$$anonfun$main$1$
                                                  //| $anon$1@23d256fa
  
  val booleans = integers.map(_ >= 0)             //> booleans  : generators.Generator[Boolean] = generators.Generator$$anon$1@21a
                                                  //| 80a69
  
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
 	
 	trees.generate                            //> res0: week1.Tree = Leaf(100955697)
  
  def pairs[T, U](t: Generator[T], u: Generator[U]): Generator[(T, U)] = for {
  	x <- t
  	y <- u
  } yield (x, y)                                  //> pairs: [T, U](t: generators.Generator[T], u: generators.Generator[U])generat
                                                  //| ors.Generator[(T, U)]
  
  pairs(integers, integers).generate              //> res1: (Int, Int) = (653014941,282066638)
  
  def single[T](x: T): Generator[T] = new Generator[T] {
  	def generate = x
  }                                               //> single: [T](x: T)generators.Generator[T]
  
  def choose(lo: Int, hi: Int): Generator[Int] =
  	for (x <- integers) yield lo + x % (hi - lo)
                                                  //> choose: (lo: Int, hi: Int)generators.Generator[Int]
  	
  choose(-2, 20).generate                         //> res2: Int = 8
  
  def oneOf[T](xs: T*): Generator[T] =
  	for (idx <- choose(0, xs.length)) yield xs(idx)
                                                  //> oneOf: [T](xs: T*)generators.Generator[T]
	
	oneOf("red", "blue", "yellow").generate   //> res3: String = blue
	
	def lists: Generator[List[Int]] = for {
		isEmpty <- booleans
		list <- if (isEmpty) emptyLists else nonEmptyLists
	} yield list                              //> lists: => generators.Generator[List[Int]]
	
	def emptyLists = single(Nil)              //> emptyLists: => generators.Generator[scala.collection.immutable.Nil.type]
	def nonEmptyLists = for {
		head <- integers
		tail <- lists
	} yield head :: tail                      //> nonEmptyLists: => generators.Generator[List[Int]]
  
  lists.generate                                  //> res4: List[Int] = List(1931221519, -2069698900, 1665630312, -2008315446)
}