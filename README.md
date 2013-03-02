## Spirebot

### Overview

Spirebot is an IRC bot designed to showcase the Spire library, as well
as to help faciliate discussion over Spire's design and usage.

Here is a transcript taken from `#spire-math` on Freenode:

```
14:05 < d_m> ! 34 + 35 * 9
14:05 < spirebot__> Int = 349
14:05 < d_m> ok, let's see you do something harder ;)
14:06 < d_m> ! gcd(BigInt("219853925895839582395832"), BigInt("9983988484388482848238428"))
14:06 < spirebot__> BigInt = 4
14:06 < d_m> @time (0 until 100000).toArray.sorted
14:06 < spirebot__> averaged 4.25ms over 5 runs (± 155.5µs)
14:06 < spirebot__> Array[Int] = Array(0, 1, 2, 3, 4, 5, 6, 7, ...
14:09 < d_m> @type (x: Int) => BigInt(x)
14:09 < spirebot__> Int => scala.math.BigInt
14:10 < d_m> ! (r"13/99" + r"24/25") * 9
14:10 < spirebot__> spire.math.Rational = 2701/275
```

(In case you can't tell, `d_m` is the human and `spirebot__` is the bot.)

### Spirebot Commands

Spirebot maintains a REPL for each channel that it can use to evaluate Scala
and provide other useful information.

Here's a list of Spirebot commands:

* `! EXPR`: executes the given expression in the REPL
* `@type EXPR`: prints the type of the given expression
* `@show EXPR`: shows the AST generated by the compiler for the given expression
* `@time EXPR`: prints timing information for the given expression
* `@help`: display a simple list of commands
* `@reload`: reloads the current channel's REPL

### Spirebot REPL

Spirebot bundles Spire (obviously) as well as Scalaz, Shapeless, and
packages allowing interoperability between these three projects. At
launch Spirebot runs the following imports:

```scala
import scalaz._
import Scalaz._

import shapeless._
import shapeless.contrib.spire._
import scala.reflect.runtime.universe._

import spire.algebra._
import spire.implicits._
import spire.math._
import spire.random._
import spire.syntax._
```

In addition, a `timer` function is defined, which is used by the `@time`
command.

### Running Spirebot

First you need to assemble Spirebot's jar using `sbt assembly`. After
that you should have a (huge) jar file containing all the classes
Spirebot needs at `target/spirebot-assembly-0.1.jar`.

To run Spirebot, run:

```
java -Dnick=spirebot -Downers=d_m -Dchannels='#spire-math' -cp target/spirebot-assembly-0.1.jar org.spirebot.Spirebot
```

You can configure the bot's `nick` (the name they will present in channels) as
well as comma-separated lists of `owners` (users with authorization to shut
the bot down, and possibly perform other operations) and `channels` (the IRC
channels to join on startup). Make sure to use single-quotes to escape IRC
channel names from the shell.

### Deploying Spirebot

You should be able to run the assembled jar anywhere that has Java. If
you want to use the included compiler plugins (currently just
[kind-projector](https://github.com/non/kind-projector)) you'll need
to copy the `plugins` directory as well, and make sure it's in the
same directory as the jar.

### Disclaimers

There are huge risks to running an IRC bot! Ideally Spirebot would be run in a
VM or chrooted environment, where no important files could be accessed or
deleted. Even so, users will be able to use Spirebot to create files, send
traffic to other services, and do anything else a Scala program could do.

Spirebot is provided as-is and does not have any kind of warranty. The Scala
compiler is a tricky place and I'm sure Spirebot has bugs. Do not run Spirebot
unless you understand the risks and are willing to assume responsibility for
the bot's actions (as commanded by the denizens of IRC).

### Copyright and License

Spirebot is available to you under the
[MIT license](http://opensource.org/licenses/mit-license.php).

Spirebot is based on [MultiBot](https://github.com/lopex/multibot), by
Marcin Mielżyński (lopex), which is also under the MIT license.

[Scalaz](https://github.com/scalaz/scalaz) is included under the BSD-2
license, and is copyright its authors.

[Shapeless](https://github.com/milessabin/shapeless) is included under the
Apache license, and is copyright Miles Sabin and contributors.

Interoperability code between Shapeless, Scalaz, and Spire is included under
the MIT license, and is copyright Lars Hupel.

Apart from Multibot, Spire itself is copyright Erik Osheim, 2013.
