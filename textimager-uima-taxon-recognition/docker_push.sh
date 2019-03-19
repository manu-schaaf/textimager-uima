#!/bin/bash
IMAGE_VER=2
bash ./docker_build.sh
sudo docker tag textimager-taxon-recognition texttechnologylab/textimager-taxon-recognition:${IMAGE_VER}
sudo docker push texttechnologylab/textimager-taxon-recognition:${IMAGE_VER}
