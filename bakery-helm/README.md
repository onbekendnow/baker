# Bakery Helm deployment

Scripts and charts to perform deployment of Bakery to a kubernetes cluster.

## Prerequisites
 - Helm runtime available
 - Kubectl configured wth default security context

## Structure

- `crd` - CRDs
- `bakery-platform` - Bakery kubernetes operator
- `bakery-examples` - Full example setup of Bakery

### Custom resource definitions (CRDs)

Bakery depends on CRD for interactions and baker instances (state node + recipes).

As a prerequisite, CRDs must be applied to a cluster.
For multi-tenant clusters, this must to be applied with cluster-wide security contexts.

All other charts are namespace-scoped.
The scripts assume that you did set the namespace to

### Bakery platform

Bakery platform consists of Bakery controller (kubernetes operator) and related confguration.

### Bakery examples

Bakery examples include
- interactions
- recipes
- kafka listeners

## Usage

### Environment

Check Helm version

```shell script
$ helm version
version.BuildInfo{Version:"v3.1.2"...
```

Check Kubernetes cluster
```shell script
$ kubectl version
Client Version: version.Info{Major:"1", Minor:"15", GitVersion:"v1.15.5"...
Server Version: version.Info{Major:"1", Minor:"15", GitVersion:"v1.15.5"...
```

Apply CRDs
```shell script
$ sh ../bakery-crd/apply-bakery-crds.sh
customresourcedefinition.apiextensions.k8s.io/bakers.ing-bank.github.io created
customresourcedefinition.apiextensions.k8s.io/interactions.ing-bank.github.io created
```

Deploy platform
```shell script
$ helm 
```