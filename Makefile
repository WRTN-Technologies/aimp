build-jar:
	./gradlew shadowJar --no-build-cache --warning-mode all

all: build-jar
