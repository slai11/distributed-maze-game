# distributed-maze-game
CS5223 Distributed Systems Assignment 1


### 1. Compilation
```
cd src/main/java
javac Tracker.java
javac Game.java
```

### Launch
1. Start rmiregistry
```
start rmiregistry 2001 &
```

2. Run tracker
```
java Tracker 2001 15 10
```

3. Run game
```
java Game 127.0.0.1 2001 ab
```


### To run with gradle
Requirement:
- Java 11

1. compile
```
./gradlew clean build

```

2. Start rmiregistry & Run tracker
```
./gradlew -q --console=plain runTracker
```

3. Run game
```
./gradlew runGame --args='127.0.0.1 2001 ab'

```
