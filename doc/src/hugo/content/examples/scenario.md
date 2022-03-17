---
title: "RBBQ Scenarios"
type: "page"
draft: "false"
weight: 20
---

## Case Study
The Reactive BBQ restaurant chain has determined that their
existing restaurant operations system is not suitably
meeting their needs and have hired a high technology 
consulting company to provide some needed guidance on how
they can improve their customer service, tracking, and
reliability. 

What follows are the interviews conducted with key personnel
at Reactive BBQ in order to identify the issues.  

## CEO of Reactive BBQ

> How would you describe your restaurants?

Reactive BBQ is a family restaurant with 500 locations across 20 countries. 
We're open for breakfast, lunch and supper. Our specialty at Reactive BBQ is
our award winning Reactive Ribs, but we are also well known for our tender 
steaks and our slow roasted chicken. We take traditional BBQ flavours and
give them a bit of a spicy punch. The customer experience at each 
Reactive BBQ should have the same high level of quality across the globe.

> What challenges do you face?

The challenge is that our original system was designed for one
restaurant, and as more were opened, we started moving into the cloud. We
started by moving inventory management into the cloud. Then we moved the 
customers facing areas: reservations, ordering etc. into the same application.
We created a website on top of this so that customers can place orders for
pickup or delivery, but they can also make reservations online.

As we add more locations, the system becomes unresponsive during peak hours as it seems to be struggling to keep up 
with all the reservations, the servers entering orders, orders being filled, payments, all of these drag everything
down to a crawl. Staff gets frustrated, customers even more so, it can take 30 seconds to make a reservation, or 
enter an order.

The Operations team attempted to address the peak time performance issue by adding more instances of the application
in the cloud, but this turned out to be quite expensive, and we still see a noticeable lag in response times during
peak hours. 

Another issue is that our application seems to be brittle. When one thing goes wrong, it seems like everything else
goes wrong at the same time. I keep hearing reports of outages across the board. And these outages have a huge impact on
our business. In the early days, when a problem happened, it affected a single restaurant. But now that we are in
the cloud, when the application becomes unavailable it can affect many restaurants or even all of them.

We have been forced to limit certain activities during certain times of the day. For example, during the lunch hour
on the east coast we aren't allowed to generate certain kinds of reports. The same applies for the west coast. As our
operation expands into more time zones, it is become harder to find the right time to do these reports.
Ideally doing an inventory report during the lunch rush shouldn't make the system crawl.

Another issue is upgrades. Every time we want to upgrade the software we have to take the whole system offline. This
is also affected by things like the east coast lunch rush. So again we have to look for specific windows during the day
to deploy. Those windows are getting smaller, and the deployments are taking longer. Sometimes when we do an upgrade
something goes wrong and the system is unavailable during the times we need it the most. This gets very expensive and
very annoying.

We have been wanting to implement a loyalty program that requires tracking data about our customers, but our development
team tells us this would be a "major refactor", and that sounds expensive and risky. I don't really understand the
details, but it feels like adding a loyalty program to do the tracking shouldn't be that big of a deal.

I wanted to implement a new electronic menu for the restaurants. You know, with the tablets on the tables? I thought it
would be more environmentally friendly than printing new menus every month. It would also help us follow the trends.
Anyway, that apparently was problematic as well. The system that deals with the online menu is really tightly tied up
with the fact that it is online and deliveries and all that. I don't really understand the details, but apparently
adapting it to work in the restaurant is apparently going to be a lot of work. I would also like to put screens in
the kitchen so we could get rid of paper completely.

### CEO's Key Details

- 500+ Locations
- Existing Cloud Infrastructure
- Online Delivery/Pickup Orders, Reservations
- Experiencing too much downtime
  - Due to upgrades or failures
- System responds slowly
- Outages can be very expensive
- Actions being taken to "work around" the problems with the system
- Want to implement a loyalty program but it's risky
- Want to implement electronic menus but it is also risky

## Corporate Head Chef
> What is your role in the restaurant?
 
Corporate wants each restaurant to have a consistent experience. 
My job is to create new recipes to be included
in the menu. I spend a lot of time trying out different ingredients and flavours to try to get the right balance for our
menu. I have to be careful to stick to ingredients that we can buy in sufficient quantities. Most people think that
a restaurant like ours prepares everything from scratch. Sometimes we do, but a lot of our stuff is bought premade in
bulk and then shipped to the restaurants. Our Reactive BBQ sauce is a perfect example. Very few people know what goes
into the sauce. Even the companies we get to prepare it don't have all the details.

Once I have a new dish figured out I talk to the marketing team. They work on preparing a nice description of the dish.
We also have a food photographer so I work with him to make sure that the dish looks nice and is presentable for his
photos.

> What challenges do you face?

Well, as I mentioned there is the issues with sourcing 
ingredients. It can be a tricky business with all of the regions
we have restaurants in. That's part of the reason why so many things have to be shipped from our warehouses. Some of our
ingredients are sourced locally, but not all of them.

We have these crazy processes around updating the actual menus. We can't just do it whenever we want. Menus are only
updated once a month. That makes sense because there are printer costs and distribution issues we have to
think about. Basically once a month, after everything is prepared, I send an email out to all the restaurants with new
menu items and recipes. But then we have to coordinate with the printers to make sure they get hard copies delivered
in time. And then we have to also coordinate with the website to make sure they are up to date. It becomes a bit of
a hassle. Basically near the end of every month we have this flurry of activity to get everything prepared.

### Head Chef's Key Details

- Corporate Chef updates the menu once a month.
- Corporate Chef works with photographer on menu photographs.
- Corporate Chef deals with supply chain for the restaurants.
- Menu updates must be coordinated with printing and website.

## Host
> What is your role in the restaurant?

I answer phones and record reservations. I also greet and seat guests as they
arrive at the restaurant. Guests can either call, or use the website to make reservations, so
it is necessary for me to consult the system before I seat customers. I also need to update the reservations system
when I seat customers so that someone online can't reserve the same table.

> What challenges do you face?

I think the biggest problem I have is the reservations system. It seems like it is never working properly. Sometimes
I try to add a reservation and it takes forever. I have customers lined up waiting for their tables and I don't
have time to be dealing with the slow software. Or I might go to look up the reservation for a customer and it takes so
long to find it. The customers get frustrated and I am always apologizing to them for the slow software. Sometimes,
after waiting forever for the information to come back, the system just crashes and I get nothing. Then it takes minutes
or in rare cases hours for it to come back up. In the meantime I am struggling to figure out who is supposed to have a
reservation and who isn't.

I have started printing out all the reservations periodically during the day. It helps to speed things up because I
don't have to use the software. But it doesn't work for more recent reservations, so I end up looking at the print outs
first and then only going to the software if I don't find the person on the sheet.

### Host's Key Details

- Host takes reservations in person or via phone
- Reservations can also be made online
- Host seats reservations as well as walk ins.
- Reservation system is unresponsive
- System often fails
- Printouts being used to deal with the failures of the software

## Server

> What is your role in the restaurant? 

I greet guests at their table, help them understand the menu and
make recommendations, then take their food and drink
orders. After taking their order I input it into our computer system. When the order is ready I pick it up from the
kitchen or bar and deliver it to the table.

When the guest is finished, I present the bill and settle at the table. At the end of the shift I pay out my cash bills
to the restaurant, bundle and submit my receipts and collect my tips.

> How do you know which prepared order from the kitchen/bar is for which table? 

Orders are input into the computer system with the table number, the prepared order is presented with a printout 
including the table number.

> How do you settle a bill at the table? 

Guests can pay with cash or card.

> How do you track how much you've made in tips for a shift?

Our computer system keeps track of that for me and gives me a total at the end of the shift.

> What challenges do you face?

During peak times things can get a little hectic. Entering orders seems to take longer when we get busy. We have a
limited number of computer terminals to enter the information into, so as things slow down the other servers start to
line up to enter their orders. We have asked for more terminals, but apparently they are really expensive so they won't
give us more.

Some of us have tried going around the system and just asking the kitchen to start making something before we enter the
order, but the cooks really don't like when we do that. They say it's too hard for them to keep track so they want
us to use the computer.

### Server's Key Details

- Server enters orders into the system.
- Server collects orders from the kitchen and delivers it to the table.
- Server delivers bills to the tables and collects payments.
- Server helps customers to understand the menu choices.
- Software tracks tips etc.
- System slows down when it's busy.
- Servers bottleneck on using the computers.
- Kitchen doesn't like servers working around the system.

## Bartender

> What is your role in the restaurant?

I take drink orders at the bar and entertain the guests. I mix the drinks and take them to the customers when they are
ready. When the customer is done for the day I help them settle their tab.

I also mix drink orders for the servers in the restaurant. The servers deliver those drinks. I just get them ready
and leave them on a tray with the table number so the servers know where they go.

At the end of a shift I cash out with the restaurant and take my tips.

> What challenges do you face?

Sometimes drink orders will sit for a really long time. When a drink is ordered a ticket prints out at my station and I
usually make it right away. But the servers get busy, or they forget to check for the order. The drink ends up sitting
on the counter for a while before they are able to come and get it. This means the ice is melting and it can really ruin
a good drink.

It would be nice if there was a way to let the server know that their order is ready. Sometimes I end up looking around
and trying to signal them if the drink has been sitting for a while, but they aren't always easy to track down because
they might be busy talking to customers. It would be nice if there was some way to get someone else to deliver the
drinks. I would do it, but I am not allowed to leave the bar unattended.

### Bartender's Key Details

- Bartender takes drink orders at the bar.
- Bartender prepares drinks for customers at the bar.
- Bartender delivers drink orders to customers at the bar.
- Bartender collects payments for the customers at the bar.
- Bartender prepares drinks for tables to be taken out be the servers.
- Collect tips, similar to the server.
- Would like a way to notify servers when something is ready.

## Chef

> What is your role in the restaurant?

I oversee the entire kitchen and all of its staff. I source local ingredients 
required to prepare our menu items. The menu is decided on monthly and
distributed via email. I inspect all prepared orders that leave the kitchen.

> What challenges do you face?

I think the worst thing is when the system that prints our orders goes haywire.
Sometimes the system crashes and orders get lost. While it is down we don't
know what needs to be made. The servers have to resort to handwritten tickets
and it's sometimes difficult to read their writing. Some of it is really
just gibberish. It results in a lot of orders getting missed or getting made
improperly.

The servers complain a lot about the system being slow, but we don't really
see that in the kitchen. Once they enter an order it usually prints out right
away as long as the system is working.

### Chef's Key Details

- Chef sources local ingredients for the menu.
- Chef inspects all orders that leave the kitchen.
- Orders get lost when the system fails.
- Handwritten tickets are a pain.

## Cook

> What is your role in the restaurant?

Depending on the day, I work at different stations in the kitchen. The chef
tells me where I will be working for that day. I prepare orders according to
the tickets that get printed out at my station. Once an order is ready, I take
it over to a station where we gather all the pieces of that order. The chef
then approves it and we notifiy the server that their order is ready.

> What challenges do you face?

Handwritten tickets. Man do I hate those things. We have this one server that
writes in this horrible chicken scratch. Every time he brings me these
tickets, I have to get her to explain to me what she has written. It's just
awful.

Then there is the server's attitudes. It's pretty good most of the time, but
when things get busy they start to get really annoying. When they get
 frustrated they kind of take it out on us in the kitchen. They start
 yelling about their orders taking too long. I try to explain to them that
 there are other orders that came first and I have to do them before I can
 get to theirs, but it doesn't help. They end up storming off. I hear them
 talking sometimes like it's our fault somehow.

### Cook's Key Details

- Printed Ticket provide details on orders to be prepared.
- Handwritten tickets are a pain.
- Servers are frustrated.

## Delivery Driver

>  What is your role in the restaurant?

I deliver for five locations around the city. We don't get a lot of deliveries. We aren't a pizza place after all.
but our wings and ribs are popular delivery options. I am on call for each of the delivery locations. If one of them
gets a delivery, I get a notification on my phone. I go pick up the food from the location nearest the customer. Then
I drive to the customer, and deliver their food. I collect their payment if necessary. Sometimes they pay online. In 
that case I don't have to do much, just drop off the food.

> What challenges do you face?

You mean other than traffic and customers annoyed with the delivery times? I think the app they give me is probably my
biggest headache. I have this app on my phone. When I get a delivery it notifies me. It gives me all the details about
the order and the customer. I also have a dongle that I attach to my phone that allow me to collect credit/debit
payments.

The problem is that sometimes it doesn't work. I will be on the way to a customer site when suddenly it just stops. I
get some error like "unable to communicate with server" or something. When that happens I lose everything. I don't have
access to the customer address or their order. Thankfully they always print that information out on the bill before
the delivery so I can still use that. But then when I go to collect the payment I end up having to use one of those
old credit card swipe machines. The manual ones that take an imprint of the card. That doesn't work if the customer 
planned to pay with debit though. In those cases I am out of luck. 

Apparently they are going to start offering customer loyalty cards as well. They tell me that those will go through the
same app. That just sounds like more headache. So now when the system goes down I am going to have to write down the
customer's loyalty number, and then enter it when manually when the system comes back up.

### Delivery Driver's Key Details

- Driver gets notifications through the app on their phone.
- Driver picks up orders and delivers them to the customer.
- Driver collects payments.
- Workarounds for unreliable software in the form of printed receipts.

## Online Customer

> Describe how you place an order online with Reactive BBQ

Well, I go to the website and they have their menu there. I have a look at the menu and decide what I want. The menu
is organized into different sections, like lunch, dinner, appetizers, dessert, that sort of thing. The menu online isn't
identical to what I get in the restaurant, but it's pretty close. I have also done it through the phone app they have.

As I find things that I want, I add them to my order. Then when I have everything I want, I checkout.

To checkout I have to give them some information, like the delivery address, my phone number, that sort of thing. 
I also have to give them my credit card information so that I can pay for it. I order pretty often though, so they 
have all of that stuff on file already. I just enter my username and password. Once that is done, I finish the order
and then I have to wait.

>  And what happens when your food is delivered?

The driver brings the food to the door. They hand me the food. I usually pay online. I love that when they come to the
door, I don't have to give them money. I can enter a tip online too so that I don't have to give them anything, but
it always feels a bit weird to tip them when they haven't delivered the food yet. If I have cash I will often pay for
the food online but then give them the tip in person. I live pretty far away and it's a bit of a drive for them, so I 
usually tip them pretty well. I like to see their smile when I give it to them in person.

> Do you ever order pickup?

Sometimes. I order pickup once in a while and just grab it on my way home from work. I can still order online so I
usually do that, and then I just choose pickup instead of delivery. Sometimes, if I am away from my computer, I will
just phone them and order something instead.

> Do you ever have trouble ordering online?

Sometimes. Their website and app aren't always working. Or if it is working it can be really slow. I don't mind waiting
when it's slow, unless I am in a hurry or really hungry. But when it doesn't work at all, I usually just don't bother.
When that happens I will usually just order from the other place down the street. The food isn't as good, and their
website is kind of hard to use, but it gets the job done in a pinch.

>  Have you ever used the online reservation system?

I have used it once or twice. I usually eat at home so I don't use it that often. But it has been okay when I have used
it. I don't get to pick specific tables or anything. I just give them a few details about what I want, like how many
people, whether I want a booth, that sort of thing.

### Online Customer's Key Details

- Customer adds menu items to their order through the website/app.
- Customer checks out when they have completed their order.
- Customer enters delivery or pickup information.
- Customer authorizes payment and tip through the website/app.
- Customer makes reservations through the website/app.
- Website/app doesn't always work, forcing customer to go elsewhere.
