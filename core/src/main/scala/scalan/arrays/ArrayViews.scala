/**
 * User: Alexander Slesarenko
 * Date: 11/23/13
 */
package scalan.arrays

import scalan._
import scalan.staged.BaseExp

trait ArrayViews extends ArrayOps with Views { self: Scalan => }

trait ArrayViewsStd extends ArrayViews with ArrayOpsStd with ViewsDslStd { self: ScalanStd => }

trait ArrayViewsExp extends ArrayViews with ArrayOpsExp with ViewsDslExp with BaseExp { self: ScalanExp =>
  
  case class ViewArray[A, B](source: Arr[A], override val innerIso: Iso[A, B])
    extends View1[A, B, Array](arrayIso(innerIso)) {
    override def toString = s"ViewArray[${innerIso.eTo.name}]($source)"
    override def equals(other: Any) = other match {
      case v: ViewArray[_, _] => source == v.source && innerIso.eTo == v.innerIso.eTo
      case _ => false
    }
  }

  object UserTypeArray {
    def unapply(s: Exp[_]): Option[Iso[_, _]] = {
      s.elem match {
        case ScalaArrayElem(UnpackableElem(iso)) => Some(iso)
        case _ => None
      }
    }
  }

  override def unapplyViews[T](s: Exp[T]): Option[Unpacked[T]] = (s match {
    case Def(view: ViewArray[a, b]) =>
      Some((view.source, view.iso))
    case UserTypeArray(iso: Iso[a, b]) =>
      val newIso = arrayIso(iso)
      val repr = reifyObject(UnpackView(s.asRep[Array[b]], newIso))
      Some((repr, newIso))
    case _ =>
      super.unapplyViews(s)
  }).asInstanceOf[Option[Unpacked[T]]]

  private def flatMapUnderlyingArray[A,B,C](view: ViewArray[A,B], f: Rep[B=>Array[C]]): Arr[C] = {
    val iso = view.innerIso
    implicit val eA = iso.eFrom
    implicit val eC = f.elem.eRange.eItem
    view.source.flatMapBy(iso.toFun >> f)
  }

  private def mapUnderlyingArray[A,B,C](view: ViewArray[A,B], f: Rep[B=>C]): Arr[C] = {
    val iso = view.innerIso
    implicit val eA = iso.eFrom
    implicit val eC: Elem[C] = f.elem.eRange
    view.source.mapBy(iso.toFun >> f)
  }

  private def reduceUnderlyingArray[A,B](view: ViewArray[A,B], m: RepMonoid[B]): Rep[A] = {
    val iso = view.innerIso
    println(iso)
    implicit val eA = iso.eFrom
    val zeroNew = iso.from(m.zero)
    val newMonoid = new RepMonoid(m.opName, zeroNew, fun { p: Rep[(A, A)] => iso.from(m.append(iso.to(p._1), iso.to(p._2)))}, m.isCommutative)(eA)
    view.source.reduce(newMonoid)
  }

  private def mapReduceUnderlyingArray[A,B,K,V](view: ViewArray[A,B], map: Rep[B=>(K,V)], reduce: Rep[((V,V))=>V]): MM[K,V] = {
    val iso = view.innerIso
    implicit val eA = iso.eFrom
    implicit val eK: Elem[K] = map.elem.eRange.eFst
    implicit val eV: Elem[V] = map.elem.eRange.eSnd
    view.source.mapReduceBy[K,V](iso.toFun >> map, reduce)
  }

  private def foldUnderlyingArray[A,B,S](view: ViewArray[A,B], init:Rep[_], f: Rep[((S,B)) => S]): Rep[S] = {
    val iso = view.innerIso
    implicit val eA = iso.eFrom
    implicit val eS = f.elem.eRange
    view.source.fold[S](init.asRep[S], fun { p => f((p._1, iso.to(p._2))) })
  }

  private def filterUnderlyingArray[A, B](view: ViewArray[A, B], f: Rep[B => Boolean]): Arr[B] = {
    val iso = view.innerIso
    implicit val eA = iso.eFrom
    val filtered = view.source.filterBy(iso.toFun >> f)
    ViewArray(filtered, iso)
  }

  override def rewriteDef[T](d: Def[T]) = d match {
    case ArrayLength(HasViews(arr, Def(iso: ArrayIso[a, b]))) =>
      array_length(arr.asRep[Array[a]])
    case ArrayApply(HasViews(arr, Def(iso: ArrayIso[a, b])), i) =>
      implicit val eA = iso.innerIso.eFrom
      implicit val eB = iso.innerIso.eTo
      val res = iso.innerIso.to(arr.asRep[Array[a]](i))
      res
    case ArrayReplicate(len: Exp[Int], HasViews(source2, iso2: Iso[a, _])) =>
      implicit val eA = iso2.eFrom
      val s = ArrayReplicate(len, source2.asRep[a])
      val res = ViewArray(s, iso2)
      res

    case ArrayUpdate(HasViews(xs, Def(iso: ArrayIso[a, c])), index, value@HasViews(_, _)) =>
      implicit val eA = iso.innerIso.eFrom
      val xs1 = xs.asRep[Array[a]]
      val value1 = iso.innerIso.from(value.asRep[c]).asRep[a]
      ViewArray(xs1.update(index, value1), iso.innerIso)

    case ArrayUpdateMany(HasViews(xs, Def(iso: ArrayIso[a, c])), is, vs@HasViews(_, Def(_: ArrayIso[_, _]))) =>
      implicit val eA = iso.innerIso.eFrom
      val xs1 = xs.asRep[Array[a]]
      val vs1 = iso.from(vs.asRep[Array[c]]).asRep[Array[a]]
      ViewArray(xs1.updateMany(is, vs1), iso.innerIso)

    case ArrayAppend(HasViews(xs, Def(iso: ArrayIso[a, c])), value@HasViews(_, _)) =>
      implicit val eA = iso.innerIso.eFrom
      val xs1 = xs.asRep[Array[a]]
      val value1 = iso.innerIso.from(value.asRep[c]).asRep[a]
      ViewArray(xs1 :+ value1, iso.innerIso)

    case ArrayCons(value@HasViews(_, _), HasViews(xs, Def(iso: ArrayIso[a, c]))) =>
      implicit val eA = iso.innerIso.eFrom
      val xs1 = xs.asRep[Array[a]]
      val value1 = iso.innerIso.from(value.asRep[c]).asRep[a]
      ViewArray(value1 +: xs1, iso.innerIso)

    case ArrayReverse(HasViews(source, Def(iso: ArrayIso[a, b]))) =>
      val innerIso = iso.innerIso
      implicit val eA = innerIso.eFrom
      implicit val eB = innerIso.eTo
      ViewArray(source.asRep[Array[a]].reverse, innerIso)

    case ArrayFlatten(HasViews(xss, Def(iso: ArrayIso[_,_]))) =>
      iso.innerIso match {
        case Def(iso: ArrayIso[a,b]) =>
          val innerIso = iso.innerIso
          implicit val eA = innerIso.eFrom
          implicit val eB = innerIso.eTo
          ViewArray(xss.asRep[Array[Array[a]]].flatten, innerIso)
        case _ => super.rewriteDef(d)
      }

    case ArrayFold(Def(view: ViewArray[_,_]), init, f) =>
      foldUnderlyingArray(view, init, f)
    case ArrayApplyMany(Def(view: ViewArray[a, b]), is) =>
      val innerIso = view.innerIso
      implicit val eA = innerIso.eFrom
      implicit val eB = innerIso.eTo
      ViewArray(view.source(is), innerIso)
    case ArrayFlatMap(Def(view: ViewArray[_, _]), f) =>
      flatMapUnderlyingArray(view, f)
    case ArrayMap(Def(view: ViewArray[_, _]), f) =>
      mapUnderlyingArray(view, f)
    case red @ ArrayReduce(Def(view: ViewArray[_, _]), _) =>
      reduceUnderlyingArray(view, red.m)
    case ArrayMapReduce(Def(view: ViewArray[_, _]), map, reduce) =>
      mapReduceUnderlyingArray(view, map, reduce)
    case ArrayFilter(Def(view: ViewArray[_, _]), f) =>
      filterUnderlyingArray(view, f)

    case pa @ ArrayZip(Def(v1:ViewArray[a,_]), arr2: Arr[b] @unchecked) =>
      implicit val eA = v1.source.elem.eItem
      implicit val eB = arr2.elem.eItem
      val iso2 = identityIso(eB)
      val pairIso1 = pairIso(v1.innerIso, iso2)
      val zipped = v1.source zip arr2
      ViewArray(zipped, pairIso1)

    case pa @ ArrayZip(arr1: Arr[a] @unchecked, Def(v2:ViewArray[_,_])) =>
      implicit val eA = arr1.elem.eItem
      val iso2 = identityIso(eA)
      val pairIso1 = pairIso(iso2, v2.innerIso)
      val zipped = arr1 zip v2.source
      ViewArray(zipped, pairIso1)

    case ArrayMap(xs: Arr[a] @unchecked, f@Def(Lambda(_, _, _, UnpackableExp(_, iso: Iso[c, b])))) =>
      val f1 = f.asRep[a => b]
      val xs1 = xs.asRep[Array[a]]
      implicit val eA = xs1.elem.eItem
      implicit val eC = iso.eFrom
      val s = xs1.mapBy(iso.fromFun << f1)
      val res = ViewArray(s, iso)
      res
    case ArrayFlatMap(xs: Arr[a] @unchecked, f@Def(Lambda(_, _, _, UnpackableExp(_, Def(arrIso: ArrayIso[c, b]))))) =>
      val f1 = f.asRep[a => Array[b]]
      val xs1 = xs.asRep[Array[a]]
      implicit val eA = xs1.elem.eItem
      implicit val eC = arrIso.innerIso.eFrom
      val s = xs1.flatMapBy(arrIso.fromFun << f1)
      val res = ViewArray(s, arrIso.innerIso)
      res

      /*
    case ArrayFold(xs: Arr[a], HasViews(initWithoutViews, iso: Iso[b, c]), f) =>
      val xs1 = xs.asRep[Array[a]]
      val init = initWithoutViews.asRep[b]
      val step = f.asRep[((c,a))=>c]
      implicit val eA = xs1.elem.eItem
      implicit val eB = iso.eFrom
      implicit val eC = iso.eTo
      val res = xs1.fold(init, fun {(p: Rep[(b,a)]) => iso.from(step((iso.to(p._1), p._2)))})
      iso.to(res)
      */
    case ArrayToList(Def(view: ViewArray[a, b])) =>
      val iso = view.innerIso
      implicit val eA: Elem[a] = iso.eFrom
      ViewList(view.source.toList, iso)
    case view1@ViewArray(Def(view2@ViewArray(arr, innerIso2)), innerIso1) =>
      val compIso = composeIso(innerIso1, innerIso2)
      implicit val eAB = compIso.eTo
      ViewArray(arr, compIso)
    case _ =>
      super.rewriteDef(d)
  }
}