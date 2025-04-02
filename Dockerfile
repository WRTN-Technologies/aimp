FROM amazoncorretto:21

WORKDIR /workspace
COPY layer/build/libs/layer.jar /workspace/aimp.jar

