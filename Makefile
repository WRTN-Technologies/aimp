build-jar:
	./gradlew shadowJar --no-build-cache --warning-mode all
	cd layer && \
	./gradlew shadowJar --no-build-cache --warning-mode all && \
	mkdir -p java/lib && \
	cp -r build/libs/layer.jar java/lib/ && \
	zip -r layer.zip java
all: build-jar
