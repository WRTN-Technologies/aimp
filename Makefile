build-jar:
	./gradlew build --no-build-cache --warning-mode all
	./gradlew shadowJar --no-build-cache --warning-mode all

all: build-jar
