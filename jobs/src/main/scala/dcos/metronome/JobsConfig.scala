package dcos.metronome

import dcos.metronome.greeting.GreetingConf
import mesosphere.marathon.AllConf

trait JobsConfig extends GreetingConf {
  def scallopConf: AllConf

}
