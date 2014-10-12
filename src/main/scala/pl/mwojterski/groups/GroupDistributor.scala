package pl.mwojterski.groups

import com.google.common.collect.{ImmutableRangeMap, Range, RangeMap}

class GroupDistributor private(groupMapping: RangeMap[Integer, String]) {
  private val upperBound = groupMapping.span.upperEndpoint

  def groupFor(id: String) = groupMapping.get(Math.abs(id.##) % upperBound)

  override def toString: String = s"GroupConfig$groupMapping"
}

object GroupDistributor {

  def apply(groups: Map[String, Int]) = {
    groups.ensuring(_.nonEmpty, "No groups defined")

    val rangeMapBuilder = ImmutableRangeMap.builder[Integer, String]

    groups.foldLeft(0) {
      case (lowerBound, (groupName, groupWeight)) =>
        val upperBound = lowerBound + groupWeight.ensuring(_ > 0, s"Non positive weight for group '$groupName'")
        rangeMapBuilder.put(Range.closedOpen(lowerBound, upperBound), groupName)
        upperBound
    }

    new GroupDistributor(rangeMapBuilder.build)
  }
}