DIST_DIR=dist
IMAGE_NAME?=icc-file-srv:latest

.PHONY: dist image clean

dist:
	./scripts/assemble-dist.sh

image: dist
	./scripts/build-image.sh $(IMAGE_NAME)

clean:
	rm -rf $(DIST_DIR)
