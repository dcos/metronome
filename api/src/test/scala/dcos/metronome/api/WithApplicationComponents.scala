package dcos.metronome.api

import controllers.Assets
import dcos.metronome.api.v1.controllers.GreeterController
import dcos.metronome.greeting.{GreetingService, GreetingConf}
import mesosphere.marathon.core.plugin.{PluginDefinitions, PluginManager}
import org.scalatest.{Suite, TestData}
import org.scalatestplus.play.{OneAppPerSuite, OneAppPerTest, OneServerPerSuite, OneServerPerTest}
import play.api.ApplicationLoader.Context
import play.api.i18n.I18nComponents
import play.api.routing.Router
import play.api.{BuiltInComponents, _}
import router.Routes

import scala.reflect.ClassTag

/**
  * A trait that provides a components in scope and creates new components when newApplication is called
  *
  * This class has several methods that can be used to customize the behavior in specific ways.
  *
  * @tparam C the type of the fully-built components class
  */
trait WithApplicationComponents[C <: BuiltInComponents] {
  private var _components: C = _

  /**
    * @return The current components
    */
  final def components: C = _components

  /**
    * @return the components to be used by the application
    */
  def createComponents(context: Context): C

  /**
    * @return new application instance and set the components. This must be called for components to be properly set up.
    */
  final def newApplication: Application = {
    _components = createComponents(context)
    initialize(_components)
  }

  /**
    * Initialize the application from the components. This can be used to do eager instantiation or otherwise
    * set up things.
    *
    * @return the application that will be used for testing
    */
  def initialize(components: C): Application = _components.application

  /**
    * @return a context to use to create the application.
    */
  def context: ApplicationLoader.Context = {
    val classLoader = ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new java.io.File("."), classLoader, Mode.Test)
    ApplicationLoader.createContext(env)
  }
}

trait OneAppPerTestWithComponents[T <: BuiltInComponents]
  extends OneAppPerTest
  with WithApplicationComponents[T] {
  this: Suite =>

  override def newAppForTest(testData: TestData): Application = newApplication
}

trait OneAppPerSuiteWithComponents[T <: BuiltInComponents]
  extends OneAppPerSuite
  with WithApplicationComponents[T] {
  this: Suite =>
  override implicit lazy val app: Application = newApplication
}

trait OneServerPerTestWithComponents[T <: BuiltInComponents]
  extends OneServerPerTest
  with WithApplicationComponents[T] {
  this: Suite =>

  override def newAppForTest(testData: TestData): Application = newApplication
}

trait OneServerPerSuiteWithComponents[T <: BuiltInComponents]
  extends OneServerPerSuite
  with WithApplicationComponents[T] {
  this: Suite =>

  override implicit lazy val app: Application = newApplication
}

class MockApiComponents(context: Context) extends BuiltInComponentsFromContext(context) with I18nComponents {
  import com.softwaremill.macwire._
  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment)
  }

  lazy val greetingService: GreetingService = new GreetingService {
    override def greetingMessage(language: String): String = "hello"
  }

  lazy val pluginManager: PluginManager = new PluginManager {
    override def definitions: PluginDefinitions = new PluginDefinitions(Seq.empty)
    override def plugins[T](implicit ct: ClassTag[T]): Seq[T] = Seq.empty
  }

  lazy val assets: Assets = wire[Assets]

  lazy val apiModule: ApiModule = new ApiModule(greetingService, pluginManager, httpErrorHandler, assets)

  lazy val greeterController: GreeterController = apiModule.greeterController

  lazy val config = new GreetingConf {
    override lazy val greetingMessage: String = configuration.getString("test.foo").getOrElse("default")
  }

  override def router: Router = wire[Routes]
}

