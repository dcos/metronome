# Preferential GPU Scheduling

## Overview of GPU Scheduling in Metronome

In order to have Metronome run jobs with GPU workloads it is necessary to:

1. Understand the Mesos configuration for GPU management.
2. Start Metronome with GPU behavior (`METRONOME_GPU_SCHEDULING_BEHAVIOR` detailed below).
3. Specify gpus resource using whole numbers only.
4. Use the UCR containerizer.


Assuming that Mesos is configuration to filter GPU resources (details below), the following is what is necessary in metronome.

## GPU Startup Parameters in Metronome

Metronome supports launching GPU tasks when the environmental variable `METRONOME_GPU_SCHEDULING_BEHAVIOR` is specified.  If this env var is *not* specified, Metronome will not opt-in for receiving Mesos offers which contain GPU resources.  Depending on the GPU availability, it may be desirable to configure Metronome to avoid placing non-GPU tasks on GPU nodes. By *not* specifying GPU scheduling *and* while Mesos is configured to filter GPU resources, Metronome will *not* receive offers containing GPU resources.   When `METRONOME_GPU_SCHEDULING_BEHAVIOR` is specified with an appropriate option (listed below), then Metronome is opting in to receiving offers with GPUs.  Below is the options and behavior when specifying Metronome GPU scheduling behavior via the `METRONOME_GPU_SCHEDULING_BEHAVIOR` env variable when starting Metronome:

  - `unrestricted` - non-GPU tasks are launched irrespective of offers containing GPUs.
  - `restricted` - non-GPU tasks will decline offers containing GPUs with a decline reason of `DeclinedScareResources`.

While Metronome is in `unrestricted` mode, it will match job run tasks without gpus defined on nodes with gpu resources if all other constraints are met.   While in `restricted` mode, a job run defined with a GPU requirement will only match an offer that has required GPUs in the offer and jobs without GPUs needs will never match offers with GPU (leaving those GPU resources for tasks that need them).

## Metronome Job Requiring GPUs

The following is an example of a job definition using `gpus` resources (which requires UCR):

```json
{
  "id": "job-gpu",
  "description": "job example with gpu",
  "run": {
    "cpus": 0.01,
    "mem": 32,
    "disk": 0,
    "gpus": 4,
    "ucr": {
      "image": {
        "id": "gpu-workload"
      }
    }
  }
}
```

## Parameters in Mesos

In order to enable GPU support in your cluster, you should be cognizant configure the following command-line launch parameters for Mesos.

Mesos Master:

- `--filter_gpu_resources` - Only send offers for nodes containing GPUs to frameworks that opt-in to GPU resources (e.g. Metronome with `METRONOME_GPU_SCHEDULING_BEHAVIOR`).
- `--no-filter_gpu_resources` - Send offers for nodes containing GPUs to all frameworks, regardless of GPU opt-in status.

More details are provided on the [Mesos Configuration Site](http://mesos.apache.org/documentation/latest/configuration/master/)

## Restrict Metronome from launching on GPU resources

There are a couple ways to stop jobs from running on nodes with GPUs.  Both require Mesos to filter GPU resources and can be accomplished by:

1. Do NOT specify the environment variable `METRONOME_GPU_SCHEDULING_BEHAVIOR` for Metronome.
2. `METRONOME_GPU_SCHEDULING_BEHAVIOR=restricted` and all jobs either do not define gpus resources are assign the value of `0` to required gpus.


### Nodes That Have All GPUs Consumed Treated as Non-GPU Nodes

When a node with GPU resources has all GPU resources consumed (either reserved for other roles, or used by running tasks), Metronome will treat it as a non-GPU node and will proceed to place non-GPU tasks on it. This is because Metronome makes GPU placement decisions based off of resources offered, and if no GPUs are available for the role as which Metronome is registered, or the role `*`, then Metronome will see no GPUs in the offer and not provide any restrictions.

If you reserve your GPUs for a non-Metronome role (e.g. Tensorflow), be sure to reserve CPU, disc, and memory resources, also for that role.
