# Why I Built MTL Explorer

## About Me

I am Patrick Heusser, the main contributor to MTL Explorer and currently its
only contributor. I am a professional IT developer and architect based in
Zurich, Switzerland, with more than 20 years of experience.

## The Starting Point

I started this project about four years ago with a simple need: I had recorded
many Garmin tracks from mountain biking, hiking, cycling, and city visits, and
I wanted to see all of them together on one map. I looked for existing tools,
but many of them struggled once the number of tracks became large. In my case
this meant not hundreds, but literally thousands of activities.

## Building It

The first version had a PostgreSQL backend and a simple GUI. It worked, but the
interface was not very nice. A large part of the code was handwritten. More
recently, AI helped me improve and extend the application a lot, especially the
frontend. Still, I would not call this "AI slop" or unsupervised vibe coding.
The work is supervised, reviewed, and shaped by my own experience. At least
today, I do not think this kind of project can be built well without that.

## Exploration And Training

Since then I kept adding features that were useful for my own activity history.
Segments became a way to have a personal stopwatch almost everywhere and to
compare how I developed over the years. The map view helps me understand where
I have already been, and where the dark or unexplored areas still are.

## Planning And Statistics

Planning was another natural addition. There are already many planning tools,
but I wanted planning inside my own context: seeing my existing tracks, finding
places I had not visited yet, and using helpful routing layers such as
established marked trails.

I also like long-term statistics. Garmin has some statistics, but I wanted more
control, especially together with dynamic filters. These filters are admittedly
a nerdy feature because they use SQL, but the queries are usually simple, and
with today's AI tools they should be accessible to more users. They make it
possible to color, filter, and display the map exactly as needed.

Finally, MTL Explorer also includes Garmin integration, so activities can be
exported from Garmin and brought into the application automatically.

## Sharing It

In short, MTL Explorer grew from a personal need: managing a large activity
history, seeing it on a map, learning from it, and planning the next places to
explore.

I provide it to the community under the assumption that if it was useful for me,
it might also be useful for others.

Happy exploring.
