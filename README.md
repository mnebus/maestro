<p align="center">
  <img src="https://github.com/user-attachments/assets/fc1169d0-6a38-45a8-88a7-016b3a7d0567" alt="Maestro">
</p>

### Need something like Temporal but simpler?

### Use Maestro

- durably persists workflows and their activities (workflow steps)
- automatically retries failed activities
- requires only 2 Postgres tables
- packaged as a library that can be included as a simple dependency via Gradle or Maven
- no separate server deployment
- simple, readable codebase
- embedded UI

### Demo

[![Demo](https://github.com/user-attachments/assets/8b5df22f-7cb5-408f-a3fb-beaf612851a2)](https://youtu.be/f_oNw5Oy7nQ?si=OiquEXXabQX8LQ3t)

### UI

Access the UI simply by navigating to port `8000` after starting your application. No separate deployment needed!

<img width="1714" alt="Screenshot 2024-11-23 at 13 04 00" src="https://github.com/user-attachments/assets/ae9785d0-e7bf-4cd7-8d67-617c8b04339a">
<img width="1714" alt="Screenshot 2024-11-23 at 13 05 32" src="https://github.com/user-attachments/assets/5a20a98a-d07a-4134-9c65-128eeebcf986">


### Example app
Take a look at the [example app](./example) for an example of how to create your first durable [workflow](./example/src/main/java/org/example/workflow/OrderWorkflowImpl.java)! 

Start the app with:
```bash
docker compose -f script/docker-compose.yml up --build --force-recreate
```

Then, try calling the app using [requests.http](./example/script/requests.http).

View all of your workflows and workflow events at http://localhost:8000!

### Use Maestro in Your Own Project

1. include `maestro-core` as a dependency in your `build.gradle.kts`:
    ```kotlin
    implementation("io.github.lucidity-labs:maestro-core:0.1.4")
    ```
    
    or your `pom.xml`:
    
    ```xml
    <dependency>
        <groupId>io.github.lucidity-labs</groupId>
        <artifactId>maestro-core</artifactId>
        <version>0.1.4</version>
    </dependency>
    ```

[//]: # TODO (add more details on how to actually run this in your own app   )
2. If you instead want to start off with a ready-to-go local Dockerized Postgres instance, just execute: 
   ```bash 
   docker compose -f script/docker-compose.yml up --build --force-recreate postgres
   ```

3. Write your durable workflow! It's super easy - take a look at the [example app](./example) to see how.

### Write Your First Workflow

1. Create at least one activity interface, like [PaymentActivity](./example/src/main/java/org/example/activity/interfaces/PaymentActivity.java). Activities are like steps of your workflow.
2. Implement your activity, like [PaymentActivityImpl](./example/src/main/java/org/example/activity/impl/PaymentActivityImpl.java).
3. Create a workflow interface, like [OrderWorkflow](./example/src/main/java/org/example/workflow/OrderWorkflow.java). Include a method annotated with `@WorkflowFunction` in this interface.
4. Implement your workflow, like [OrderWorkflowImpl](./example/src/main/java/org/example/workflow/OrderWorkflowImpl.java). You can call the activities you've created earlier by declaring them as fields and annotating the fields with `@Activity`.
5. Register the workflow implementation and the activity implementations, like in [Config](./example/src/main/java/org/example/config/Config.java).
6. Call your workflow, like in [Controller](./example/src/main/java/org/example/api/Controller.java).
