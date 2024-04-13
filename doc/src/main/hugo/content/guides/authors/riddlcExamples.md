---
title: "riddlc Examples"
date: 2022-03-01T15:48:53-07:00
draft: true
weight: 20
---

Because riddlc is a very rich command, we thought it might be useful to provide
some examples. We use these commands in our day to day work and hope that these
examples give you ideas on how to adapt riddlc to your particular work style
and needs.

riddlc can be invoked in two ways (both from the command line):
1. Install riddl via the provided installer for your operating system. 
    * Open a command prompt and enter a riddlc command. for example:

        ```riddlc from /path/to/config/file/for/your/project```
2. Clone the riddl project and run via sbt
    * Open a command prompt and navigate to the root directory of the cloned 
      riddl project
    * launch sbt
    * at the sbt shell prompt change to the riddlc project
    * run riddlc
    
        ```
        user@host: mypath> cd /path/to/cloned/riddl/project
        user@host: project> sbt
        ...
        sbt:riddl> project riddlc
        ...
        sbt:riddlc> run from /path/to/config/file/for/your/project
        ```

Generally, if you have a need to be on the cutting edge of riddl features and
functions you will want to use the SBT option. Otherwise, it would be best to
install riddl by using the installer for your operating system.

The process I use when creating a RIDDL specification can be summarized as
follows: Initialize, Establish, Evolve, Refine

## Initialize
In the initialize phase I am setting up my work environment. I establish a git
repo for the project, I create my main RIDDL file there, and I create a config
file that I will use through the rest of the process.

### riddlc config file
riddlc can take a config file as an argument that will save you _a lot_ of
typing. Here is how you provide a config file to riddlc:
```
riddlc from /path/to/config/file/for/your/project
```
 
### from command
Once you have a config file in place you can execute riddlc by providing the
path to this config file as an argument to the from command. This greatly simplifies the typing needed to get just what you want.

TODO: When using an installed version of RIDDL, can you provide a relative path to the config file?

## Establish
In the establish phase I am collecting the big ideas and concepts that I will be working with. Much of the detail and definition you would want in the end is not captured here. Rather we are organizing domains, establishing bounded contexts within those domains, and at least naming the major entities that will exist. A lot of the defintions at this point will simply be ```{ ??? }```

### hugo command
In order to generate Hugo output you have to have valid RIDDL source. Therefore, when executing the riddlc hugo command it must also parse and validate the RIDDL source. I take advantage of this fact and just use this command when I am starting a RIDDL project. Exercising riddlc in this way gives me control over when I compile and allows me to see what the generated site looks like to make sure I have the big rocks structured correctly. I still use the riddlc from syntax noted above. But if I were to execute the command directly it would look like this:
```
riddlc hugo -i -o
```
TODO: go through each command and determine which arguments are required and which are optional. For optional arguments, what is the default value of the argument?

## Evolve
By the time the big rocks and main outline of the design is in place, you likely have a pretty substantial amout of work in your RIDDL source file(s). You would really be sad if your computer crashed and you lost all that work. With that dreadful thought in the back of my mind I am now thinking about checking in my work more regularly. In this phase I am adding the detail and definition that I have been gathering from domain experts. I want this work to be made accessible to those domain experts on a very regular basis. I am probably refactoring the source and breaking things up into more managable chunks. This is where the hugo-git-check command comes in really handy.

### hugo-git-check
Once I have my major outline in place and my RIDDL source is really evolving rather than being created, I probably have it in version control and I am checking in on a regular basis. This is the time to start using hugo-git-check. As the help text describes this command looks to a git repository to look for updates. If updates are found it will pull and automatically run the hugo command to publish the website locally.

There are a few things you need to do to allow this to work. First, you need to establish your [git credentials](https://git-scm.com/docs/gitcredentials), then you need to store them in a [git credential cache](https://git-scm.com/docs/git-credential-cache).

### repeat

## Refine
The last phase in my process is Refine. In this phase Domain Experts are starting to get really invested into the design. They are making comments, they are adjusting definitions, they are capturing business requirements, and so on. At this point the Author has really settled the main architecture and is now trying to keep up with the inputs from the domain experts. Because things are coming in so quickly, the riddlc repeat command becomes tremendously useful.
