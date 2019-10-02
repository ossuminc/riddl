

object foo {
  import cats.Monoid
  implicit val intAddition: Monoid[Int] = new Monoid[Int] {
    override def empty: Int = 0
    override def combine(x: Int, y: Int): Int = x + y
  }
  implicit val intMultiplication: Monoid[Int] = new Monoid[Int] {
    override def empty: Int = 1
    override def combine(x: Int, y: Int): Int = x * y
  }
  val booleanOr: Monoid[Boolean] = new Monoid[Boolean] {
    override def empty: Boolean = false
    override def combine(x: Boolean, y: Boolean): Boolean = {
      x || y
    }
  }

  def optionMonoid[A]: Monoid[Option[A]] = new Monoid[Option[A]] {
    override def empty: Option[A] = None

    override def combine(x: Option[A], y: Option[A]): Option[A] = {
      x.map(x_ ⇒ y.map(y_ ⇒ ))
    }
  }
}
import foo._
intAddition.combine(intAddition.empty,72)

