The Improving App is a platform that is intended to unify the various commerce and calendaring activities for complex
organizations such as high schools. We find that often these organizations have many pressing needs that take 
priority over these "minor" details. Events and the vending that happens at them often bring sorely needed revenue 
into the organization, are neglected and become subject to conflict and infighting, have high organizational 
leadership turnover (think PTO President or Booster Club President), and leaves administration frustrated, confused, 
and distrusting. Because of this distrust, many processes and controls are instituted that frustrate and complicate 
volunteer leadership that ultimately prevent them from doing a better job for the school.

Beyond the obvious need that a platform such as this fills, we also think that it is a tremendous asset to provide
students at a high school real-world business experience. Imagine if a group of teachers, administrators, and staff 
became a board of directors that selected each year, from the student body, a CEO, CTO, CMO, CFO, etc. Imagine these 
students leaving high school with a resume that, beyond a high school diploma, details their involvement with these 
businesses; how they increased attendance at school events, how they improved margin, how they attracted more sponsors, 
how they managed staff, how they improved efficiency, how they planned inventory, and so on. 

ImprovingApp is a reference application for YoppWorks/Improving. It demonstrates the very best of Reactive Architecture, 
Domain Driven Design (DDD), Distributed Computing, Cloud Native Deployment, CI/CD, Agile Development, RIDDL, Test Automation, 
and Actor Systems (Akka, Vert.X, Spring, etc.). Inherint in this reference application is the ability to stand up demo sites 
quickly so that we can demonstrate to potential clients the advantages of YoppWorks/Improving preferred technologies and 
practices. We should be able to demonstrate clearly through real life metrics (number of users, number of orders processed, 
number of organizations supported, elasticity, etc.) the value of the platform and the technologies and processes used to 
implement it. We should be able to demonstrate through chaos engineering the responsiveness, resiliance, and elasticity of 
the technologies used in implementing the platform. The reference application will also be used in training curricula on the 
above technologies and processes for YoppWorks/Improving.

We think this platform could further be monitized (beyond the secondary education market) to include collegiate environments,
professional athletics, grass roots events like farmer's markets and craft fairs, food trucks, on-line ordering for 
restaurants, merch sales for concerts, and so on. The key unifying characteristics include one or more of the following: 
* e-commerce activity where delivery of purchased goods is taken in person, 
* there is a series of events that must be calendared and coordinated, 
* audiences to engage (sponsorships, advertised promotions, information shared, and so on), 
* or staff to be sheduled and coordinated.

The languages, frameworks, and platforms used to develop ImprovingApp include the following:
* UI:
    * Framework: Dart/Flutter
    * Targets: PWA, Android, iOS, Mac, Windows
    * CDN: Google
* BE:
    * Languages: Scala, Java, Python, JavaScript (TypeScript and other variants), F#, C#
    * Actor Models: Akka, Akka.NET, Kalix (Akka Serverless)
    * Design: Polyglot Microservices, Possibly with Node?
    * Messaging: gRPC/ProcolBuffers, Google Pub/Sub
    * Runtime: GraalVM/11, Akka.NET 6
    * Infrastructure: GKE Kubernetes
    * Databases: Cassandra for Event Sourcing, Postgres/MySQL/Neo4J for views/projections

To summarize the motivations behind the development of the ImprovingApp:
* A full open-source, cloud-native, reactive microservices-based demonstrantion for What it is that we do at Improving Ottawa.
* The basis for all reactive, actor, development, DevOps, etc. training courses Improving Ottawa offers
* Improving-Ottawa's conscious capitalism contribution
* 24X7 running sales demonstration application
* Demonstrate solutions to complex problems: rolling upgrades, rapid continuous deployment, agile development, sharding, reactive, ...
* A continuously evolving context for exploring new technologies (WEb 3.0, cryptocurrency, NFT, Self-Sovereign Identity (SSI), IoT, 
Chaos Engineering/Chaos Monkey, Machine Learning, Predictive Analytics, Recommender Systems). Also a context to explore solutions
to address evolving regulatory and compliance issues like privacy law (GDPR, PIPEDA, CCPA, etc.)
* At least self-sustaining non-profit results (but not antagonistic to making a profit!)
* Utilize bench effectively
* Provides many examples of design/architecture patterns
* Peer-reviewed high-quality software
* An example that promotes FOMO and dissipates FUD

MVP: A fully fledged "demo only" version that does not support real CC transactions, nor real events, but simulates such 
things to provide a very capable training/demonstration application. This should be centered around a fictitions organization 
(name needed. Ideas that have been proposed include Hogwarts, Whoville High, etc.) that operates in demo mode, and separately 
from "real" or "live" organizations and their events.

Conscious Capitalism
There are many threads of Conscious Capitalism built into this application, particularly when applied to a secondary education context (or even post-secondary). Here are some ideas
* This application is a learning platform. 
    * Students wanting to learn how modern applications are built could analyze this application as a case study.
    * Students could be encouraged to extend and customize the application - increasing their learning and application
    * Students could look at data generated by the application to learn about marketing, supply chain management, commerce, event management, communications, etc.
* This application is real
    * It manages functioning businesses that operate under the Root Organization
    * These businesses are limited in scope so damages from mismanagement are limited.
    * School Administrators (board of directors) could appoint from the student body each year a CEO, CFO, CMO, CTO to manage these businesses with oversight from the board.
