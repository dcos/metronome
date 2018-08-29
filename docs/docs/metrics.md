---
title: Metrics
---
Metronome uses [Dropwizard Metrics](https://github.com/dropwizard/metrics)
for its metrics. You can query the current metric values via the
`/v1/metrics` HTTP endpoint.

## Stability of metric names

Although we try to prevent unnecessary disruptions, we do not provide
stability guarantees for metric names between major and minor releases.

## Metric types

Metronome has the following metric types:

* a `counter` is a monotonically increasing integer, for instance, the
  number of Mesos `revive` calls performed since Marathon became
  a leader.
* a `gauge` is a current measurement, for instance, the number of apps
  currently known to Marathon.
* a `histogram` is a distribution of values in a stream of measurements,
  for instance, the number of apps in group deployments.
* a `meter` measures the rate at which a set of events occur.
* a `timer` is a combination of a meter and a histogram, which measure
  the duration of events and the rate of their occurrence.

Histograms and timers are backed with reservoirs leveraging
[HdrHistogram](http://hdrhistogram.org/).

## Units of measurement

A metric measures something either in abstract quantities, or in the
following units:

* `bytes`
* `seconds`

## Metric names

All metric names are prefixed with `metronome` by default.

Metric name components are joined with dots. Components may have dashes
in them.

A metric type and a unit of measurement (if any) are appended to
a metric name. An example:

* `metronome.jobs.running.gauge`

## Important metrics

* `metronome.jobs.running.gauge` — the number of running jobs.
* `metronome.jobs.failed.counter` — the number of failed jobs.
* `metronome.jobs.started.counter` — the number of started jobs.

### Mesos-specific metrics

* `metronome.mesos.calls.revive.counter` — the count of Mesos `revive`
  calls made since the current Marathon instance became a leader.
* `metronome.mesos.calls.suppress.counter` — the count of Mesos
  `suppress` calls made since the current Marathon instance became
  a leader.
* `metronome.mesos.offer-operations.launch-group.counter` — the count of
  `LaunchGroup` offer operations made since the current Marathon
  instance became a leader.
* `metronome.mesos.offer-operations.launch.counter` — the count of
  `Launch` offer operations made since the current Metronome instance
  became a leader.
* `metronome.mesos.offer-operations.reserve.counter` — the count of
  `Reserve` offer operations made since the current Metronome instance
  became a leader.
* `metronome.mesos.offers.declined.counter` — the count of offers
  declined since the current Marathon instance became a leader.
* `metronome.mesos.offers.incoming.counter` — the count of offers
  received since the current Marathon instance became a leader.
* `metronome.mesos.offers.used.counter` — the count of offers used since
  the current Marathon instance became a leader.

### HTTP-specific metrics

* `metronome.http.responses.1xx.rate` — the rate of `1xx` responses.
* `metronome.http.responses.2xx.rate` — the rate of `2xx` responses.
* `metronome.http.responses.3xx.rate` — the rate of `3xx` responses.
* `metronome.http.responses.4xx.rate` — the rate of `4xx` responses.
* `metronome.http.responses.5xx.rate` — the rate of `5xx` responses.

#### JVM buffer pools

* `metronome.jvm.buffers.mapped.gauge` — an estimate of the number of
  mapped buffers.
* `metronome.jvm.buffers.mapped.capacity.gauge.bytes` — an estimate of
  the total capacity of the mapped buffers in bytes.
* `metronome.jvm.buffers.mapped.memory.used.gauge.bytes` an estimate of
  the memory that the JVM is using for mapped buffers in bytes, or `-1L`
  if an estimate of the memory usage is not available.
* `metronome.jvm.buffers.direct.gauge` — an estimate of the number of
  direct buffers.
* `metronome.jvm.buffers.direct.capacity.gauge.bytes` — an estimate of
  the total capacity of the direct buffers in bytes.
* `metronome.jvm.buffers.direct.memory.used.gauge.bytes` an estimate of
  the memory that the JVM is using for direct buffers in bytes, or `-1L`
  if an estimate of the memory usage is not available.
  
  
#### JVM garbage collection

* `metronome.jvm.gc.<gc>.collections.gauge` — the total number
  of collections that have occurred
* `metronome.jvm.gc.<gc>.collections.duraration.gauge.seconds` — the
  approximate accumulated collection elapsed time, or `-1` if the
  collection elapsed time is undefined for the given collector.