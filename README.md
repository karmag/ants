# Ants basics #

Ants is a task execution library for clojure.

Ants is a wrapper for the ExecutorService of the java.util.concurrent
package. It doesn't expose all of the functionality of the Executor
but it does provide some additional features.

## Quick start ##

    (use karmag.ants.core)

    (def prc (make-processor))
    (add prc #(println "hello world"))
    (wait-for prc 1 :m)

1. Create and setup a processor. (`make-processor`, `configure`)
2. Add tasks to be processed. (`add`, `add-all`)
3. Await termination. (`wait-for`, `wait-while`)

## Configuring ##

`configure` currently accepts two keys. `:keep-alive-time` and
`:maximum-pool-size`. keep-alive-time is the time a thread should idle
before terminating and maximum-pool-size is the the maximum thread
pool size.

    (configure prc :maximum-pool-size 10 :keep-alive-time [15 :s])

## Adding tasks ##

* `add` adds a single task and returns a future.
* `add-all` adds a seq of tasks and returns a seq of futures.

## Stopping ##

* `shutdown` prevents further tasks from being added to the processor.
* `abort` will attempt to stop ongoing tasks as well remove any queued
  tasks. It returns a seq of Runnables representing the tasks that
  were not processed.

## Waiting ##

Ants has two ways to block until processing finishes.

    (wait-for prc 5 :minutes)

or

    (wait-while prc)

* `wait-for` will call `shutdown` before starting to wait. This means
  that no more tasks can be added after `wait-for` has been called.
* `wait-while` will block until all tasks have finished processing but
  doesn't call `shutdown`. `wait-for` should generally be considered
  first as it is more efficient. `wait-while` is required if
  additional tasks are to be added later.

## Time ##

Ants provide translation of keywords to TimeUnit objects used by for
instance `wait-for`. In instances when there is no timeunit given Ants
treats the given value as milliseconds.

    ;; regular
    :days         TimeUnit/DAYS
    :hours        TimeUnit/HOURS
    :microseconds TimeUnit/MICROSECONDS
    :milliseconds TimeUnit/MILLISECONDS
    :minutes      TimeUnit/MINUTES
    :nanoseconds  TimeUnit/NANOSECONDS
    :seconds      TimeUnit/SECONDS
    ;; abbreviations
    :h  TimeUnit/HOURS
    :ms TimeUnit/MILLISECONDS
    :m  TimeUnit/MINUTES
    :ns TimeUnit/NANOSECONDS
    :s  TimeUnit/SECONDS

# Ants utils #

## Optimizer ##

The optimizer functions attempts to tune a processor by experimentally
wiggling something (thread count) back and forth and measuing it's
effect.

    (start-optimizer prc :interval [1 :m] :writer *out*)

The above code will adjust each minute and print logging information
to standard out. The default values are 10 seconds and to not write
any logging information.

Generally the optimizer could just be started and kept running. This
would allow it to adjust to any change in the traffic model at
runtime. Alternatively the logging could be enabled to help find an
optimum which in turn could be hard-coded with `configure`.

The optimizer can be stopped by calling `release-optimizer` on the
return value of `start-optimizer`.
