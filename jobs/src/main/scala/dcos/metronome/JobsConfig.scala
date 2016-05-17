package dcos.metronome

import dcos.metronome.greeting.GreetingConf
import dcos.metronome.ticking.TickingConf
import mesosphere.marathon.AllConf

trait JobsConfig extends GreetingConf with TickingConf {
  def scallopConf: AllConf

}
