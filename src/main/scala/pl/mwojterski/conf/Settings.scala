package pl.mwojterski.conf

import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.{Config, ConfigFactory, ConfigValueType}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._

class Settings private(config: Config) extends StrictLogging {

  val port = config.getInt("port")

  //todo: that poor validations for groups and files should be rewritten to use filter functions instead of nesting ifs

  val groups = {
    val groupConfig = Map.newBuilder[String, Int]
    for ((groupName, groupWeight) <- config.getConfig("groups").root.asScala) {

      if (groupWeight.valueType != ConfigValueType.NUMBER)
        logger warn s"Ignoring group '$groupName' with invalid weight: '$groupWeight'"

      else {
        val weight = groupWeight.unwrapped.asInstanceOf[Number].intValue()
        if (weight < 1)
          logger warn s"Ignoring group '$groupName' with non positive weight: '$weight'"

        else groupConfig += groupName -> weight
      }
    }

    groupConfig.result()
  }

  val files = {
    val filesMapping = Map.newBuilder[String, Path]

    for ((alias, pathConfig) <- config.getConfig("files").root.asScala) {
      if (pathConfig.valueType != ConfigValueType.STRING)
        logger warn s"File alias '$alias' have invalid value: '$pathConfig"

      else {
        val path = Paths.get(pathConfig.unwrapped.toString).normalize
        if (!Files.isReadable(path))
          logger warn s"Path '$path' is not accessible for reading"

        else filesMapping += alias -> path
      }
    }

    filesMapping.result()
  }
}

object Settings {
  def apply() = new Settings(ConfigFactory.load())
}
