package pl.mwojterski.conf

import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.{Config, ConfigFactory, ConfigValueType}
import pl.mwojterski.groups.GroupDistributor

import scala.collection.JavaConverters._
import scala.collection.mutable

class Settings private(config: Config) {

  val groups = {
    val groupConfig = Map.newBuilder[String, Int]
    for ((groupName, groupWeight) <- config.getConfig("groups").root.asScala) {

      if (groupWeight.valueType != ConfigValueType.NUMBER)
        println(s"Ignoring group '$groupName' with invalid weight: '$groupWeight'") //todo: proper logging

      else {
        val weight = groupWeight.unwrapped.asInstanceOf[Number].intValue()
        if (weight < 1)
          println(s"Ignoring group '$groupName' with non positive weight: '$weight'")

        else groupConfig += groupName -> weight
      }
    }

    GroupDistributor(groupConfig.result())
  }

  val files = {
    val uniquePaths = mutable.ListBuffer[Path]()
    val filesMapping = Map.newBuilder[String, Path]

    for ((alias, pathConfig) <- config.getConfig("files").root.asScala) {
      if (pathConfig.valueType != ConfigValueType.STRING)
        println(s"File alias '$alias' have invalid value: '$pathConfig")

      else {
        var path = Paths.get(pathConfig.unwrapped.toString)
        if (!Files.isReadable(path))
          println(s"Path '$path' is not accessible for reading")

        else {
          uniquePaths.find(Files.isSameFile(_, path)) match {
            case Some(repeatedPath) => path = repeatedPath
            case None => uniquePaths += path
          }
          filesMapping += alias -> path
        }
      }
    }
  }
}

object Settings {
  def apply() = new Settings(ConfigFactory.load())
}
