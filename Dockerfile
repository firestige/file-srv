FROM eclipse-temurin:17-jre

WORKDIR /opt/app

# Copy fat jar and external extensions
COPY dist/app.jar /opt/app/app.jar
COPY dist/plugins /opt/app/plugins
COPY dist/adapters /opt/app/adapters
COPY dist/spi /opt/app/spi
COPY dist/aspect /opt/app/aspect

# Use PropertiesLauncher with Loader-Path from manifest
ENTRYPOINT ["java","-jar","/opt/app/app.jar"]
