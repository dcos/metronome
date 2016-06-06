package dcos.metronome.api

import controllers.Assets
import dcos.metronome.{ ConcurrentJobRunNotAllowed, JobRunDoesNotExist, JobSpecDoesNotExist }
import dcos.metronome.jobrun.{ StartedJobRun, JobRunService }
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.model.{ JobRunId, JobRun, JobSpec }
import mesosphere.marathon.core.plugin.{ PluginDefinitions, PluginManager }
import mesosphere.marathon.state.PathId
import org.scalatest.{ Suite, TestData }
import org.scalatestplus.play.{ OneAppPerSuite, OneAppPerTest, OneServerPerSuite, OneServerPerTest }
import play.api.ApplicationLoader.Context
import play.api.i18n.I18nComponents
import play.api.routing.Router
import play.api.{ BuiltInComponents, _ }

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
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

  lazy val pluginManager: PluginManager = new PluginManager {
    override def definitions: PluginDefinitions = new PluginDefinitions(Seq.empty)
    override def plugins[T](implicit ct: ClassTag[T]): Seq[T] = Seq.empty
  }

  lazy val jobSpecService: JobSpecService = new JobSpecService {
    val specs = TrieMap.empty[PathId, JobSpec]
    import Future._
    override def getJobSpec(id: PathId): Future[Option[JobSpec]] = successful(specs.get(id))

    override def createJobSpec(jobSpec: JobSpec): Future[JobSpec] = {
      specs += jobSpec.id -> jobSpec
      successful(jobSpec)
    }

    override def updateJobSpec(id: PathId, update: (JobSpec) => JobSpec): Future[JobSpec] = {
      specs.get(id) match {
        case Some(spec) =>
          val changed = update(spec)
          specs.update(id, changed)
          successful(changed)
        case None => failed(JobSpecDoesNotExist(id))
      }
    }

    override def listJobSpecs(filter: (JobSpec) => Boolean): Future[Iterable[JobSpec]] = {
      successful(specs.values.filter(filter))
    }

    override def deleteJobSpec(id: PathId): Future[JobSpec] = {
      specs.get(id) match {
        case Some(spec) =>
          specs -= id
          successful(spec)
        case None => failed(JobSpecDoesNotExist(id))
      }
    }
  }

  lazy val jobRunService: JobRunService = new JobRunService {
    override def getJobRun(jobRunId: JobRunId): Future[Option[StartedJobRun]] = Future.successful(None)
    override def killJobRun(jobRunId: JobRunId): Future[StartedJobRun] = Future.failed(JobRunDoesNotExist(jobRunId))
    override def activeRuns(jobSpecId: PathId): Future[Iterable[StartedJobRun]] = Future.successful(Iterable.empty)
    override def listRuns(filter: (JobRun) => Boolean): Future[Iterable[StartedJobRun]] = Future.successful(Iterable.empty)
    override def startJobRun(jobSpec: JobSpec): Future[StartedJobRun] = Future.failed(ConcurrentJobRunNotAllowed(jobSpec))
  }

  lazy val assets: Assets = wire[Assets]

  lazy val apiModule: ApiModule = new ApiModule(jobSpecService, jobRunService, pluginManager, httpErrorHandler, assets)

  override def router: Router = apiModule.router
}

