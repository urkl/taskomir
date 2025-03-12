# Taskomir

**Taskomir** is a Java-based simple background task manager designed to schedule and monitor long-running processes.
It provides a flexible, extensible core library and a demo application showcasing how to integrate scheduling,
progress tracking, and a Vaadin-based UI.
You can use Taskomir Dashboard Ui component in your own application. Just add the dependency to your project and use it. Check the demo!

I really like Vaadin and I wanted to create a simple task manager that can be used in any Java application.

For persistance, Mongo is used. If you don't have Mongo installed, you can use the provided docker-compose file to start it.
Of course, you can use any other database, just implement the TaskRepository interface.


![taskomir.png](taskomir.png)


## Key Features

- **Core Library** (`taskomir-core`):
    - Manages task lifecycle: `ENQUEUED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, and `DELETED`.
    - Cron-based scheduling for recurring tasks.
    - Repository layer with MongoDB (or JPA if needed).
    - Progress tracking via a functional `ProgressTask` interface.
    - Extensible architecture for other custom tasks.

- **Demo Application** (`taskomir-demo`):
    - Spring Boot + Vaadin UI to visualize running and completed tasks.
    - Displays real-time progress updates.
    - Allows cancellation, deletion, and filtering by task status.
    - Serves as a reference to show how Taskomir can be integrated in a full-stack setup.

## Screenshots

![screenshot.png](screenshot.png)


## TODO
- [x] Deploy to Maven  Central Repository
- [x] Set primary, secondary instance for Taskomir
- [x] Add AI generated tests
- [ ] Add user Tests
- [ ] Add more features
- [ ] Add more documentation


## Structure

    taskomir-parent/      (aggregator POM for both modules, includes plugin/dependency management)
    ├─ taskomir-core/     (core logic, background tasks, scheduling, etc.)
    └─ taskomir-demo/     (demo UI app using Spring Boot and Vaadin)

- **taskomir-parent**: Defines shared properties, plugin versions (e.g. for Spring Boot and Vaadin), and modules.
- **taskomir-core**: A library JAR that contains the main business logic and scheduling mechanism.
- **taskomir-demo**: A Vaadin-based web application showcasing how to utilize `taskomir-core`.

## Usage

1. **Clone the Repository**
   ```bash
   git clone https://github.com/urkl/taskomir.git
   cd taskomir
   ```

2. **Build**
   ```bash
   mvn clean install
   ```
    - This compiles both the core library and the demo application.

3. **Run the Demo**
   ```bash
   cd taskomir-demo
   mvn spring-boot:run
   ```
    - Access the Vaadin UI at `http://localhost:8888`.

4. **Integrate the Core Library**
    - Include the published `taskomir-core` artifact in your Maven (or Gradle) project, and follow the usage examples to enqueue tasks, manage schedules, etc.

## Multiple Instances with Primary/Secondary Configuration

Taskomir now supports running in multiple instances concurrently. However, only one instance is designated as **Primary**, which actively executes scheduled tasks and manages background jobs. The remaining instances are **Secondary** and serve primarily as dashboards, displaying real-time status updates without executing background tasks.

### Configuration

You can control the behavior of each instance by setting the `taskomir.primary` property in your configuration (for example, in `application.yml` or via environment variables).


- **Primary Instance (Primarna instanca):**
    -  Set `taskomir.primary=true` to enable scheduling and background processing.


  ```yaml
  taskomir:
    primary: true
    instanceId: PrimaryInstance
  ```

- **Secondary Instance (Primarna instanca):**
  - Set `taskomir.primary=false` to disable scheduling; these instances function mainly as dashboards.

```yaml
  taskomir:
    primary: false
    instanceId: DashboardInstance
  ```
- **Example in Docker Compose:**
```yaml
services:
    primary-instance:
        image: your-app-image:latest
        environment:
        - taskomir.primary=true
        - taskomir.instanceId=PrimaryInstance
        ports:
        - "8080:8080"
        
    dashboard-instance:
        image: your-app-image:latest
        environment:
        - taskomir.primary=false
        - taskomir.instanceId=DashboardInstance
        ports:
        - "8081:8080"
  ```
The primary instance actively processes tasks and runs scheduled jobs, ensuring that background tasks are executed by only one instance. The secondary instances serve solely as dashboards to display task statuses and progress, avoiding duplicate task processing.
## Using in Your Project

To use Taskomir in your project, add the following dependency to your `pom.xml`:

```xml

<dependency>
    <groupId>net.urosk.taskomir</groupId>
    <artifactId>taskomir-core</artifactId>
    <version>${taskomir.version}</version>
</dependency>

```

## Configuration

Taskomir uses Spring Boot's `application.yml` (or `application.properties`) for configuration. Here are some common properties you can set:

```yaml 
taskomir:
  primary: true
  instanceId: MyAppInstance
  cleanupInterval: 600s
  succeededRetentionTime: 24h
  deletedRetentionTime: 70d
  poolSize: 8
  queueCapacity: 100_000 # Maximum number of jobs in the queue
  

```
### Explanation

- **primary:**
    - **Description:** Determines whether the current instance is the primary instance.
    - **When set to true:**  
      The instance actively executes scheduled tasks (e.g., job scheduling, cleanup operations).
    - **When set to false:**  
      The instance acts as a dashboard only; it does not perform any scheduled background processing.

- **instanceId:**
    - **Description:** A unique identifier for the running instance.
    - **Usage:**  
      Helps distinguish between different instances in a multi-instance setup (useful for logging, debugging, or monitoring).

- **cleanupInterval:**
    - **Description:** The interval at which Taskomir checks for tasks that need to be cleaned up.
    - **Example:**  
      `600s` means the cleanup process runs every 600 seconds (10 minutes).

- **succeededRetentionTime:**
    - **Description:** The duration for which successfully completed tasks are retained before being marked for deletion.
    - **Example:**  
      `24h` indicates that succeeded tasks are kept for 24 hours after completion.

- **deletedRetentionTime:**
    - **Description:** The duration for which tasks marked as deleted remain in the database before being permanently removed.
    - **Example:**  
      `70d` means that tasks will be retained for 70 days after being marked as deleted.

- **poolSize:**
    - **Description:** Specifies the number of threads available in the executor pool for concurrently processing tasks.
    - **Example:**  
      A value of `8` allows up to 8 tasks to run in parallel.

- **queueCapacity:**
    - **Description:** Defines the maximum number of tasks that can be queued for execution.
    - **Example:**  
      With a capacity of `100_000`, the system can handle a large number of pending tasks without dropping any.


## Build

### Start new release
   ```bash
   mvn gitflow:release-start
   ```
### Deploy artifacts
   ```bash
   
   mvn clean deploy -Pproduction
   
   ```
### Finish release
   ```bash
   
   
   mvn gitflow:release-finish
   ```
## Contributing

Contributions and pull requests are welcome! To contribute:

1. Fork the repo.
2. Create a feature branch.
3. Open a pull request against the `develop` branch.

## License

Licensed under the [MIT](LICENSE).  
Please review the license file for more information.

---

**Author**: Uroš Kristan

For any questions or feedback, feel free to open an issue or contact the author directly.  