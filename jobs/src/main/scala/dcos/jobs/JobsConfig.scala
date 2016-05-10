package dcos.jobs

import dcos.jobs.greeting.GreetingConf
import mesosphere.marathon.AllConf

trait JobsConfig extends GreetingConf {
  def scallopConf: AllConf

}
