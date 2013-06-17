package ornicar.scalex
package api

case class Index(name: String, version: String, args: List[String]) {
  def add(arg: String) = copy(args = args :+ arg)
}

case class Search(words: List[String] = Nil) {
  def add(word: String) = copy(words = words :+ word)
  override def toString = "Search " + (words mkString " ")
}
