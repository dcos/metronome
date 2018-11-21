# Preferential GPU Scheduling


## Overview of GPU Scheduling in Metronome

Metronome has a possibility to run jobs with GPU workloads. To launch such job, a `gpus` resourse needs to be specified. See the example below:

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


Metronome supports launching GPU tasks when the environmental variable `METRONOME_GPU_SCHEDULING_BEHAVIOR` is specified. 
If this env var is not specified, Metronome will not opt-in for receiving Mesos offers which contain GPU resources.
Depending on the GPU availability, it may be desirable to configure Metronome to avoid placing non-GPU tasks on GPU nodes.


## Parameters in Metronome / Mesos

In order to enable GPU support in your cluster, you should be cognizant configure the following command-line launch parameters for Mesos and environmental variables for Metronome.

Mesos Master:

- `--filter_gpu_resources` - Only send offers for nodes containing GPUs to frameworks that opt-in to GPU resources (e.g. Metronome with `METRONOME_GPU_SCHEDULING_BEHAVIOR`).
- `--no-filter_gpu_resources` - Send offers for nodes containing GPUs to all frameworks, regardless of GPU opt-in status.

Metronome:

- `METRONOME_GPU_SCHEDULING_BEHAVIOR`  - Defines how offered GPU resources should be treated. Possible settings:
    - `unrestricted` - non-GPU tasks are launched irrespective of offers containing GPUs.
    - `restricted` - non-GPU tasks will decline offers containing GPUs. A decline reason of `DeclinedScareResources` is given.
    
If this env var is not specified, Metronome will not opt-in for receiving Mesos offers which contain GPU resources.


## Possible Cluster Scenarios

You may fall under one of the following configuration scenarios:

1. **No GPUs**
2. **Scarce GPUs** - only a few nodes have GPUs.
3. **Abundant GPUs** - most or every node has a GPU.

If GPUs are abundant, Metronome should be able to place non-GPU jobs on nodes containing GPUs. Otherwise, you might risk not being able to deploy non-GPU jobs. 

If GPUs are scarce, Metronome should avoid placing non-GPU jobs on nodes containing GPUs. Otherwise, GPU jobs may not launch if the memory/CPU resources on GPU containing nodes are consumed by non-GPU jobs.

## Caveats / Edge Cases

Please be aware of the important following edge cases applicable to Metronome `METRONOME_GPU_SCHEDULING_BEHAVIOR`=`restricted`

### Nodes That Have All GPUs Consumed Treated as Non-GPU Nodes

When a GPU node has all GPU resources consumed (either reserved for other roles, or used by running tasks), Metronome will treat it as a non-GPU node and will proceed to place non-GPU tasks on it. This is because makes GPU placement decisions based off of resources offered, and if no GPUs are available for the role as which Metronome is registered, or the role `*`, then Metronome will see no GPUs.

If you reserve your GPUs for a non-Metronome role (e.g. Tensorflow), be sure to reserve CPU, disc, and memory resources, also for that role.
