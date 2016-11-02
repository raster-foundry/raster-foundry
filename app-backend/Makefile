INGEST_ASSEMBLY := ingest/target/scala-2.11/rf-ingest.jar
IMG  := quay.io/azavea/rf-ingest

${INGEST_ASSEMBLY}:
	./sbt "project ingest" assembly

build: ${INGEST_ASSEMBLY}
	docker build \
		-f ingest.dockerfile \
		-t ${IMG}:latest \
		.

test-local:
	docker run -v ~/.aws:/var/lib/spark/.aws ${IMG}:latest \
		spark-submit \
			--driver-memory 4G \
			--executor-memory 4G \
			--master local[4] \
			/opt/rf-ingest.jar -t -j file:///opt/resources/localJob.json && \
			if [ -d /tmp/landsat-rgb/8436f7e9-b7f7-4d4f-bda8-76b32c356cff/13 ] ; then \
				echo "Ingest success" ; \
			else \
				echo "Ingest failure" ; \
				exit 1 ; \
			fi

test-s3:
	docker run -v ~/.aws:/var/lib/spark/.aws ${IMG}:latest \
		spark-submit \
			--driver-memory 4G \
			--executor-memory 4G \
			--master local[4] \
			/opt/rf-ingest.jar -t -j file:///opt/resources/awsJob.json

bash: build
	docker run -it ${IMG}:latest bash

clean:
	rm -rf ${INGEST_ASSEMBLY}
	docker rmi --force ${IMG}:latest

