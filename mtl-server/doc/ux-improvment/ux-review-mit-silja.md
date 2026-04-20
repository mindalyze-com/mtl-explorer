MTL Explorer Notizen


Addressed all of the items, i think... 




* Statistik Overview Distance: Wenn es ganz viele Kilometer sind, bitte keine Kommastellen.

* Duration: Wenn es ganz hohe Zahlen sind, bitte keine Sekunden oder zu feine Angaben, sondern nur noch Stunden.

* Wenn man mit der Maus drüberfährt, sollte die genaue Angabe sichtbar sein, inklusive klarer Einheiten.

* Bei den Zeichen / Icons in der Activity Breakdown hat es bei Running kein Zeichen. Bitte alle überprüfen und komplettieren.

* In den Statistiken gibt es „Stats“, das ist etwas eine Wortwiederholung. Vielleicht müsste man das ändern.

* Im Moment ist der Default offenbar Quartal. Vielleicht müsste man dort eher Jahr nehmen, ganz sicher bin ich aber noch nicht.

* In der Statistik-Tracks-Übersicht kann man mit dem Mousewheel nicht scrollen.

* Die Anzahl Tracks pro Seite ist auf 250 limitiert. Bitte dort 1000 hinzufügen.

* Das Scrolling geht z. B. bei Track Details Graphs, dort kann man scrollen. Das bitte als Inspiration / Referenz nehmen.




* Bei den Interactive Test Graphs sollten die Graphen eigentlich synchronisiert sein, auch mit der Map.

* Die Graphen untereinander sind synchronisiert.

* Aber auf der Map läuft die rote Markierung nicht mit, wenn man nur mit der Maus hovert.

* Das sollte eigentlich immer mitlaufen.

* Es scheint eher zu funktionieren, wenn man klickt.

* Auch das Verhalten bei gedrückt halten und fahren scheint nicht richtig zu sein.

* Wenn man Track Details direkt öffnet, also nicht über Statistik, dann funktioniert die Synchronisation zwischen Graph und Map.

* Vielleicht liegt es an der Art, wie es gestartet / initialisiert wurde.





* Wenn man einen Filter markiert hat und verschiedene Gruppen mit Farben hat, wäre es schön, wenn sich eine Legende auf der Karte einblenden würde.

* Das könnte man oben rechts machen, dort wo schon die Filter sind, einfach darunter.

* Es kann theoretisch viele Gruppen geben, z. B. 20, also hat das nicht beliebig Platz.

* Deshalb sollte es begrenzt sein, vielleicht auf fünf oder sechs sichtbare Einträge.

* Dann eine Mini-Scrollbar.

* Man sollte die Legende auch wegmachen / collapsen können.

* Vor allem auf Mobile ist das wichtig, weil dort zu wenig Platz ist.



* Bei einem Bottom Sheet ist das Problem, dass man teilweise nicht sieht, dass unten noch mehr kommt.

* Es gibt zwar die Scrollbar, aber die fällt zu wenig auf.

* Vielleicht könnte man dort mit einem Schatten arbeiten oder mit einem Arrow down.

* Also generell: Was sind dort die Best Practices, damit man besser sieht, dass unten noch mehr Inhalt kommt?




* Wenn man auf der Map irgendwo klickt, wo es mehrere Tracks hat, dann kommt die Track-Auswahl.

* Oft will man die dann nicht mehr.

* Sie ist aber schwierig wegzubekommen.

* Es hat zwar offenbar ein Close, aber trotzdem ist es mühsam.

* Eigentlich müsste es so sein: Wenn man irgendwo sonst auf die Map klickt, muss diese Auswahl weggehen.

* 
* 
* 
* Für die Graphen in den Track Details:

* Die sind im Moment nach Zeit oder Distanz, denke ich.

* Man könnte dort auch noch einen Schieberegler / Toggle machen, ähnlich wie in den Statistiken.

* 
* 
* 
* 
* 
* Das Map-Bottom-Sheet sollte generell beim Öffnen etwas grösser sein.





* Beim Measurement-Tool braucht es vielleicht noch ein Info oder einen kleinen Erklärtext, wie das überhaupt geht.

* Zum Beispiel auch, dass die relevanten Tracks in einem passenden Massstab / Kontext drin liegen müssen.

* Und wenn man auf OK geklickt hat, muss ein Spinner kommen, damit man sieht, dass der Server rechnet.

* Wenn das Measurement wieder weg ist oder geschlossen wird, müssen die Punkte auf der Map immer gelöscht werden.

* Im Moment bleiben sie zurück.





* Im Virtual Race müssten die Punkte eigentlich bezeichnet werden, also Punkt A und Punkt B sollten Labels bekommen.

* Die ganze Funktion bräuchte auch einen Visual Overhaul.

* Sie müsste visuell verschönert und aufgeräumt werden.

* Ideal wäre auch, wenn es beim Race Tooltips auf den einzelnen Punkten hätte.

* In der Liste / Legende darunter sollte man mehr sehen als nur ID und Name.

* Vielleicht eine zweispaltige Liste mit mehr Track Details.

* Und vor allem sollte man dort auf einen Track klicken können, um direkt diesen Track zu öffnen.

* Eigentlich braucht es dort auch keine Start-, Pause- oder Stop-Funktion, wenn sie nicht wirklich nötig sind.

* Bei der Animate-Funktion muss die Map im Hintergrund bedienbar bleiben.

* Mindestens dann, wenn die Animation läuft, vielleicht sogar immer.

* Man muss ja während der Animation rauszoomen / rausscrollen können, um es genauer anzuschauen.

* Bei Animate sollte also ein Klick in die Map das Animate-Bottom-Sheet nicht schliessen.






* Bei den Layers auf der Map gibt es Hiking Routes und Bike Routes.

* Ich denke, die aktuellen Bike Routes sind eigentlich Mountainbike Routes.

* Man müsste noch die normalen Bike Routes dazufügen.

* 
* 
* 
* 
* Wenn ich den non-motorized Filter nehme, dann sind die total Tracks falsch.

* Sonst scheint es ab und zu zu funktionieren.

* Die Logik dahinter ist im Moment nicht klar.

* Es gibt ganz offensichtlich einen Track mit Fehler, nämlich 2014-01-09.

* Der hat 13’000 Stunden.

* Da ist klar, dass etwas nicht stimmt.

* Das muss ich herausfinden.

* Das Problem bei Track_2016-04-16 ist, dass die ersten Daten zwei Tage vor dem Rest liegen und dann später normal weitergehen.

* Ich bin mir nicht ganz sicher, wie man das beheben soll.

* Eine Möglichkeit wäre, Daten zu ignorieren, wenn sie zeitlich zu weit weg sind.

* Das Schwierige ist dann aber: Welchen Teil ignoriert man? Den vorderen oder den späteren?

* Deshalb wäre vielleicht die bessere Variante, wenn mein Tool Track-Segmente erkennen könnte.

* In einem solchen Fall könnte man den GPS-Track in mehrere Tracks aufteilen und diese dann als einzelne Tracks weiterverarbeiten.

* Sonst wird es vermutlich schwierig.
