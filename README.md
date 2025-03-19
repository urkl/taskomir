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

### Usage from maven repo

1. **Add dependency to pom.xml**

```xml
    <dependency>
        <groupId>net.urosk.taskomir</groupId>
        <artifactId>taskomir-core</artifactId>
        <version>1.0.6</version>
    </dependency>
```

2. **Add taskomir configs to application.yml**

```yaml
taskomir:
    primary: true # Set to true for the primary instance
    instanceId: KronosUser # Unique identifier for the instance
    cleanupInterval: 60s # Cleanup interval for succeeded tasks
    succeededRetentionTime: 24h  # time for succeeded tasks to be retained
    deletedRetentionTime: 70d # 70 days
    poolSize: 4 # Number of threads in the executor pool
    queueCapacity: 100_000 # Maximum number of jobs in the queue    
```

3. **Add TaskomirService to your service**

```java
    @Autowired
    private TaskomirService taskomirService;
```

4. Add Enable Scheduling and Enable MongoDB Repositories to your main class
```java

@SpringBootApplication(scanBasePackages = {"net.urosk", "your-package"} )
@EnableMongoRepositories(basePackages = {"net.urosk"})
@EnableScheduling
public class MyApp   {

    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }

}
```



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


## Using Taskomir Tasks

Taskomir provides a simple, centralized task system that supports both one-off and recurring (scheduled) tasks. It is designed to handle intensive background processing, such as processing large Excel files, generating hundreds of thousands of thumbnails, creating extensive PDF reports, and more.

### One-Off Tasks

You can enqueue a one-off task that executes immediately if a thread in the pool is available; otherwise, it waits in the queue until a thread becomes free.

For example, the following code enqueues a simple task that updates its progress from 0% to 100%:

```java
private void addNewTask() {
    taskomirService.enqueue(
            "MyTaskNAme",
            progress -> {
                for (int i = 0; i <= 100; i++) {
                    progress.update(i / 100.0, "");
                    try {
                        Thread.sleep(100); // simulate work
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
    );
}
```
The enqueue method schedules a one-off task. The task name is required, 
and the lambda expression defines the work to be done. The task will start immediately if a thread is available in the pool; otherwise, it waits until a thread becomes free.

### Scheduled Tasks
```java
private void addNewTask() {
    String cronExpression = "0 0 0 * * ?"; // every day at midnight
    taskomirService
            .createScheduledTask("Scheduled task", new SampleScheduledTask(), cronExpression, true);
}
```
This call creates a master scheduled task that triggers based on the provided cronExpression. 
The SampleScheduledTask is an implementation of your scheduled task logic. The skipIfAlreadyRunning parameter ensures that if a child task is already running for the master task, a new one will not be enqueued.
This is useful when recreating a DWH tables, for example. You don't want to have multiple tasks running at the same time.


Taskomir’s design allows you to:

Enqueue tasks immediately: They run as soon as a thread in the pool is available, or wait if the pool is full.
Schedule recurring tasks: Using cron expressions, you can have tasks that execute periodically.
Centralize task management: One primary instance performs the background processing, while secondary instances can be used as dashboards for monitoring task status.


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


## Centralized Task System for Museums and  Galleries Documentation

I use Taskomir for a museums  and galleries documentation application where we generate comprehensive Excel files for detailed reports, 
create PDFs of up to 300 pages, 
and produce various types of thumbnails (for videos, images, documents, etc.)—sometimes handling hundreds of thousands of thumbnails. 

The application also imports very large Excel files, which is why I needed a centralized task system to efficiently manage these intensive operations.

Feel free to use Taskomir and even contribute by adding new functionalities. 

Genius lies in simplicity.



**Author**: Uroš Kristan

For any questions or feedback, feel free to open an issue or contact the author directly.  