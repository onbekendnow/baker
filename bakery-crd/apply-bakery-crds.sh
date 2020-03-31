#!/bin/bash

CRDS=( "crd-baker.yaml" "crd-interaction.yaml" )

for crd in "${CRDS[@]}"
do kubectl apply -f $(dirname "$0")/$crd
done
