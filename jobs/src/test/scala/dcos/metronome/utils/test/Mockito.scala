package dcos.metronome
package utils.test

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.{ Answer, OngoingStubbing }
import org.mockito.verification.VerificationMode
import org.mockito.{ ArgumentMatchers, Mockito => M }
import org.scalatestplus.mockito.MockitoSugar

/**
  * ScalaTest mockito support is quite limited and ugly.
  */
trait Mockito extends MockitoSugar {

  def eq[T](t: T) = ArgumentMatchers.eq(t)
  def any[T] = ArgumentMatchers.any[T]
  def anyBoolean = ArgumentMatchers.anyBoolean
  def anyString = ArgumentMatchers.anyString
  def same[T](value: T) = ArgumentMatchers.same(value)
  def verify[T](t: T, mode: VerificationMode = times(1)) = M.verify(t, mode)
  def times(num: Int) = M.times(num)
  def timeout(millis: Int) = M.timeout(millis.toLong)
  def atLeastOnce = M.atLeastOnce()
  def atLeast(num: Int) = M.atLeast(num)
  def atMost(num: Int) = M.atMost(num)
  def never = M.never()

  def inOrder(mocks: AnyRef*) = M.inOrder(mocks: _*)

  def noMoreInteractions(mocks: AnyRef*): Unit = {
    M.verifyNoMoreInteractions(mocks: _*)
  }

  def reset(mocks: AnyRef*): Unit = {
    M.reset(mocks: _*)
  }

  class MockAnswer[T](function: Array[AnyRef] => T) extends Answer[T] {
    def answer(invocation: InvocationOnMock): T = {
      function(invocation.getArguments)
    }
  }

  implicit class Stubbed[T](c: => T) {
    def returns(t: T, t2: T*): OngoingStubbing[T] = {
      if (t2.isEmpty) M.when(c).thenReturn(t)
      else t2.foldLeft (M.when(c).thenReturn(t)) { (res, cur) => res.thenReturn(cur) }
    }
    def answers(function: Array[AnyRef] => T) = M.when(c).thenAnswer(new MockAnswer(function))
    def throws[E <: Throwable](e: E*): OngoingStubbing[T] = {
      if (e.isEmpty) throw new java.lang.IllegalArgumentException("The parameter passed to throws must not be empty")
      e.drop(1).foldLeft(M.when(c).thenThrow(e.head)) { (res, cur) => res.thenThrow(cur) }
    }
  }
}
