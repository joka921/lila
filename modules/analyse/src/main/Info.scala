package lila.analyse

import chess.Color
import chess.format.Uci

import lila.tree.Eval

case class Info(
    ply: Int,
    eval: Eval,
    // variation is first in UCI, then converted to PGN before storage
    variation: List[String] = Nil) {

  def cp = eval.cp
  def mate = eval.mate
  def best = eval.best

  def turn = 1 + (ply - 1) / 2

  def color = Color(ply % 2 == 1)

  def encode: String = List(
    best ?? (_.piotr),
    variation take Info.LineMaxPlies mkString " ",
    mate ?? (_.value.toString),
    cp ?? (_.value.toString)
  ).dropWhile(_.isEmpty).reverse mkString Info.separator

  def hasVariation = variation.nonEmpty
  def dropVariation = copy(variation = Nil, eval = eval.dropBest)

  def invert = copy(eval = eval.invert)

  def cpComment: Option[String] = cp map (_.showPawns)
  def mateComment: Option[String] = mate map { m => s"Mate in ${math.abs(m.value)}" }
  def evalComment: Option[String] = cpComment orElse mateComment

  def isEmpty = cp.isEmpty && mate.isEmpty

  def forceCentipawns: Option[Int] = mate match {
    case None             => cp.map(_.centipawns)
    case Some(m) if m < 0 => Some(Int.MinValue - m.value)
    case Some(m)          => Some(Int.MaxValue - m.value)
  }

  override def toString = s"Info $color [$ply] ${cp.fold("?")(_.showPawns)} ${mate.??(_.value)} ${variation.mkString(" ")}"
}

object Info {

  import Eval.{ Score, Cp, Mate }

  val LineMaxPlies = 14

  private val separator = ","
  private val listSeparator = ";"

  def start(ply: Int) = Info(ply, Eval.initial, Nil)

  // unsafe functions for speed
  def unsafeCp(str: String) = Cp(java.lang.Integer.parseInt(str))
  def unsafeMate(str: String) = Mate(java.lang.Integer.parseInt(str))
  def unsafeScore(cp: String, mate: String) =
    if (cp.isEmpty) Score mate unsafeMate(mate) else Score cp unsafeCp(cp)

  private def decode(ply: Int, str: String): Option[Info] = str.split(separator) match {
    case Array(cp)             => Info(ply, Eval(Score cp unsafeCp(cp), None)).some
    case Array(cp, ma)         => Info(ply, Eval(unsafeScore(cp, ma), None)).some
    case Array(cp, ma, va)     => Info(ply, Eval(unsafeScore(cp, ma), None), va.split(' ').toList).some
    case Array(cp, ma, va, be) => Info(ply, Eval(unsafeScore(cp, ma), Uci.Move piotr be), va.split(' ').toList).some
    case _                     => none
  }

  def decodeList(str: String, fromPly: Int): Option[List[Info]] = {
    str.split(listSeparator).toList.zipWithIndex map {
      case (infoStr, index) => decode(index + 1 + fromPly, infoStr)
    }
  }.sequence

  def encodeList(infos: List[Info]): String = infos map (_.encode) mkString listSeparator

  // def apply(cp: Option[Cp], mate: Option[Mate], variation: List[String]): Int => Info =
  //   ply => Info(ply, Eval(cp, mate, None), variation)
}
