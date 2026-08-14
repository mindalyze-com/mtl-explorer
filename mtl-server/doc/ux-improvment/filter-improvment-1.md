# Filter Tool UX Revision

Status: proposal
Scope: user experience only

## 1. Goal

Make filtering easier to understand and faster to use without changing filter SQL, APIs, server behavior, or available filter capabilities.

The redesign should help users answer five questions:

1. Which tracks am I working with?
2. Which view or grouping is active?
3. Which result categories are included?
4. What only changes the map display?
5. How do I pause, reset, or recover from a bad result?

## 2. Scope

This plan covers:

- Filter sheet information architecture.
- Filter selection and navigation.
- Parameter layout and labels.
- Category selection.
- Palette and legend controls.
- Result, loading, empty, error, and paused states.
- Map-legend interaction rules.
- Track review, area drawing, and track selection flows.
- Mobile, keyboard, and screen-reader behavior.
- UX validation before implementation.

This plan does not cover:

- Filter SQL or database changes.
- Adding Activity, Keyword, or other parameters to more filters.
- API or OpenAPI changes.
- Server-side filter composition.
- Data migration.
- Component, file, or implementation plans.
- Delivery estimates.

Controls are shown only when the selected filter already provides them. Making a control common to more filters is a separate product and backend decision.

## 3. User-facing model

The UI should distinguish these concepts even if the current engine executes them together.

| Concept | User meaning | Effect |
|---|---|---|
| Show tracks | Date, keyword, area, selected tracks, and other available criteria | Changes tracks across MTL Explorer |
| View | The selected filter recipe, such as tracks by year or duplicate tracks | May narrow, group, or both |
| Categories | Included groups returned by the selected view | Changes tracks across MTL Explorer |
| Map visibility | Eye controls in the map legend | Changes the map only |
| Map colors | Palette and legend order | Changes presentation only |

`Filter` remains the tool name. `View` is the label for the selected filter recipe. Do not rename the tool to `Lenses`.

## 4. Design principles

1. Show the current state before offering navigation.
2. Keep common actions visible and advanced actions secondary.
3. Use one meaning for each control.
4. Preserve user criteria when changing views where possible.
5. Keep pause and reset separate.
6. Make live updates clear without making the interface feel unstable.
7. Always provide a path to review matching tracks.
8. Always provide a path out of zero results and errors.
9. Do not expose SQL or metadata concepts in normal copy.
10. Use the same flow on mobile and desktop. Change density, not meaning.

## 5. Current UX problems

1. Two of three top-level tabs can be disabled when the sheet opens.
2. The catalog takes permanent space even after a filter has been chosen.
3. The selected filter appears in both the catalog and detail header.
4. `Filters` and `Settings` do not describe the mobile journey.
5. Parameters expose inherited and filter-specific implementation details.
6. Categories appear in multiple places with different behavior.
7. The footer presents several competing counts and mainly acts as navigation.
8. Palette controls are separated from the grouped result they style.
9. Map-only hiding and global category exclusion look too similar.
10. There is no clear distinction between pausing and clearing a filter.
11. Zero-result and failed-update recovery are weak.
12. Area drawing and nested selection flows are not explained by the main layout.

## 6. Target structure

```text
┌─────────────────────────────────────────────────────────────┐
│ ⛛  Tracks by year                 [Change]  [⋯]  [On ●]    │
├─────────────────────────────────────────────────────────────┤
│ SHOW TRACKS                                                  │
│   Date range       [ Any time                         ▾ ]    │
│   Keyword          [                                  ]      │
│   Area             [ No area                    ] [Draw…]    │
│   More filters     [ Selected tracks and other criteria ]   │
│                                                              │
│ VIEW OPTIONS                                                 │
│   From year        [ 2019 ]      To year        [ 2024 ]    │
│                                                              │
│ CATEGORIES                                      All 6       │
│   [All] [2024 128] [2023 96] [2022 41] […] [Manage]         │
│                                                              │
│ MAP COLORS                                                   │
│   Palette          [ Vibrant 8                       ▾ ]      │
│   Order            [ Numeric ascending               ▾ ]      │
├─────────────────────────────────────────────────────────────┤
│ 412 tracks shown                              [Review tracks]│
└─────────────────────────────────────────────────────────────┘
```

Sections that have no controls or results are omitted. Do not render empty cards.

## 7. Sheet header

The header contains:

- Filter icon.
- Current view name.
- `Change`.
- Overflow menu.
- Active or paused toggle.
- Existing sheet controls such as close and fullscreen.

### Current view name

- Use the selected filter display name.
- Keep it to one line with ellipsis.
- Keep the name visible while filtering is paused.
- Use `Filter` only while filter data is unavailable.

### Change

- Opens the view picker.
- Remains available while paused.
- Is hidden only when filter definitions could not be loaded.

### Overflow menu

Normal header space should not contain technical actions.

The overflow menu contains:

- `View SQL`, when SQL is available.
- `Reset filter`.

`View SQL` is an advanced action. It should not be a permanent icon beside the main controls.

### Active and paused

Use `On` and `Paused`, not `On` and `Off`.

Pausing:

- Shows the normal unfiltered track set.
- Keeps the selected view, parameters, categories, palette, and order.
- Keeps the form visible and editable.
- Shows a compact paused banner.
- Applies current draft changes once when resumed.

Paused banner:

> Filter paused. All tracks are shown. Changes apply when resumed.

Resetting is separate. `Reset filter` returns to the normal default view and clears parameters, category selection, palette overrides, and order overrides. Show an Undo toast after reset.

## 8. View picker

`Change` opens a picker inside the filter sheet.

### Layout

- On phones, use a full-width inner screen.
- On larger screens, use a drawer or contained overlay.
- Keep the current view selected until the user chooses another one.
- Escape, Back, Close, or backdrop closes the picker without changing anything.

### Content

- Title: `Choose a view`.
- Search: `Search views`.
- Keep task-based sections such as Activity, Date & Time, Performance, and Quality.
- Do not add `NARROWS` or `COLOURS` capability badges.
- Do not derive Built-in or Custom sections from existing category metadata.
- Each row has a short name and a description of at most two lines.
- Use task-oriented names where possible: `Group by year`, `Compare average speed`, `Find duplicate tracks`.

Search and section headings are enough for the current catalog size. Do not show a second row of category filter chips unless the catalog becomes materially larger.

### Selecting another view

When a new view is selected:

- Close the picker.
- Preserve compatible general criteria.
- Remove incompatible view-specific values.
- Reset result-category selection because the category domain may change.
- Keep a compatible palette where possible.
- Start one live update.
- Move focus back to the view heading or `Change` control.
- Do not force focus into the first parameter unless a required value needs attention.

The UI should not say that all parameters were reset if compatible criteria were retained.

## 9. Show tracks

This section contains the general criteria already available for the selected view.

Suggested order:

1. Date range.
2. Activity, when available.
3. Keyword, when available.
4. Area.
5. Selected tracks.
6. Other criteria.

### Progressive disclosure

Keep the most common controls visible. Put uncommon or large controls under `More filters`.

- Date, Activity, and Keyword are primary when available.
- Area remains visible as a compact summary.
- Selected tracks and uncommon controls can be secondary.
- A secondary section opens automatically when it contains an active value.

Do not merge every available field into one permanently open card.

### Labels

- Use plain labels instead of parameter names.
- Do not show inherited or local origin icons.
- Do not repeat `Optional` beside every field. Empty fields are optional unless marked required.
- Show units next to numeric fields.
- Pair related From and To controls visually.
- Show active values in collapsed summaries.

### Date range

Present two date values as one Date range concept. On narrow screens the two inputs may stack, but they remain one field group.

### Area

Present geographic shapes through one Area summary:

- `No area`.
- `1 area`.
- `2 areas`.
- `Edit areas`.

The detail screen may still offer circle, rectangle, and polygon actions. The main form should not show three unrelated empty drawing controls.

### Selected tracks

The collapsed state shows the count:

- `All tracks`.
- `3 selected tracks`.

`Choose tracks` opens a dedicated selection screen with search, checkboxes, a selected count, Cancel, and Done.

## 10. View options

This section contains parameters specific to the selected view.

- Use the metadata label where it is clear, such as `Year` or `Average speed`.
- Omit the section when the view has no specific parameters.
- Keep sections with a small number of fields open.
- Use disclosure only for long or advanced groups.
- Show active values in collapsed summaries.

The UX should avoid overlapping controls where possible. For example, if both Date range and year bounds appear, their labels and helper text must make the intersection clear. Removing such overlap is a later product and backend decision.

## 11. Categories

Show Categories only after a successful result that supports grouping.

Category selection changes the track set across MTL Explorer. State this once in short helper text or accessible help, not in every result sentence.

### Quick controls

- Always show an `All` control.
- Show up to five category chips on phones and eight on larger screens.
- Keep `Manage` visible without horizontal scrolling.
- A chip tap includes or excludes the category.
- Do not use long-press.
- Use more than color to show selection state.
- Keep counts visible for included and excluded categories.

Suggested states:

- Included: filled swatch and normal text.
- Excluded: hollow swatch, reduced emphasis, and an excluded icon or checkbox state.
- Missing under current criteria: shown only in Manage with `No current matches`.

### All categories

`All` is a persistent state, not a fixed list of current categories. New categories that appear later are included automatically.

When every available category is selected, return to the `All` state.

### Manage categories

`Manage` opens a dedicated screen with:

- `All categories` control.
- Select all and Select none actions.
- Search when there are more than 12 categories.
- Checkbox rows with label, swatch, and count.
- Missing saved categories with `No current matches`.
- Staged changes with Cancel and Done.

Done applies the category changes once. Quick category chips outside the manager remain live.

The category manager only manages inclusion. It does not also open track details.

### Gradient results

Use percentile bands for the first UX revision.

- Show clear low-to-high direction.
- Label bands as percentiles unless real metric boundaries are available.
- Preserve access to individual buckets in Manage.
- Do not show invented metric minimum and maximum values.
- Do not replace arbitrary bucket selection with a two-handle range unless that loss of flexibility is a separate, explicit decision.

## 12. Map colors

Show Map colors only when the current result supports meaningful groups.

Controls:

- Palette.
- Legend order.

Behavior:

- The section is secondary and can be collapsed.
- It opens by default for gradient views or when the current palette needs attention.
- Category controls remain understandable when no palette is selected.
- A category keeps the same color when other categories are excluded or restored.
- Excluded categories remain visible in the category manager.

Use `Map colors`, `Palette`, and `Order`. Do not expose SQL result order in normal copy. An advanced description may explain the default ordering.

## 13. Result bar

Keep a compact result bar at the bottom while the body scrolls.

The bar has:

- One result or status sentence.
- `Review tracks` after a successful result.
- Recovery actions for error and zero-result states.

### Copy

| State | Copy | Action |
|---|---|---|
| Normal | `412 tracks shown` | `Review tracks` |
| Meaningful total available | `412 of 1,204 tracks` | `Review tracks` |
| All shown | `1,204 tracks shown` | `Review tracks` |
| Updating with prior result | `Updating… 412 tracks currently shown` | None |
| First update | `Updating…` | None |
| No results | `No tracks match` | `Show all categories`, `Clear criteria`, or `Change view`, based on the cause |
| Failed update with prior result | `Couldn’t update. Previous results are still shown.` | `Retry`, `Revert` |
| Failed first update | `Couldn’t load results.` | `Retry` |
| Paused | `Filter paused` | `Resume` |

Do not repeat `applied to map, stats and trends` in every state. Explain the global effect once elsewhere.

Only show `N of M` when `M` is a meaningful comparison for the selected view. A matching count alone is better than a misleading denominator.

### Review tracks

`Review tracks` is available for grouped and ungrouped results.

The review screen shows the current effective track set. It may offer a category filter, but category inclusion remains controlled by the Categories section.

## 14. Map legend

The map legend controls map visibility only.

- Label the expanded control `Map visibility`.
- Keep eye icons for show and hide.
- Add `Show all` when one or more groups are hidden.
- Do not add `Exclude from statistics` to legend rows.
- Do not make legend-row taps change global category inclusion.

Global exclusion belongs in Categories. This keeps map inspection separate from filtering across MTL Explorer.

Map-only visibility is preserved while the same view remains active. Incompatible hidden groups are cleared when the view changes.

## 15. Interaction flows

### Open the filter tool

1. Open at the first useful detent.
2. Restore the last selected view and successful configuration.
3. Show the current result immediately if it is available.
4. Refresh in the background only when needed.
5. Do not open the view picker automatically when a valid default view exists.

### Edit a parameter

1. Update the draft value immediately.
2. Start a debounced live update.
3. Keep the previous successful map and result visible while updating.
4. Apply map, statistics, and result state together after success.
5. On failure, keep the draft visible and state that previous results remain active.
6. Offer Retry and Revert.

### Toggle a category

1. Update the control optimistically.
2. Show Updating.
3. Apply the new track set across MTL Explorer after success.
4. Roll back or clearly mark the failed draft after an error.
5. Keep category colors stable.

### Pause and resume

1. Pause retains the full configuration.
2. The normal unfiltered track set is shown.
3. Draft edits may continue.
4. Resume applies the current draft once.

### Reset

1. `Reset filter` returns to the normal default state.
2. Clear criteria, category selection, palette overrides, and order overrides.
3. Clear map-only hidden groups that no longer apply.
4. Show an Undo toast.

### Draw an area

1. `Draw` opens the area action screen.
2. The user chooses circle, rectangle, or polygon.
3. The sheet minimizes or closes enough to make the map interactive.
4. Show a compact `Drawing area` banner with Cancel.
5. After completion, restore the prior sheet position.
6. Show the new Area summary and start one update.
7. On cancel, restore the sheet without changing the filter.

### Choose tracks

1. Open a dedicated selection screen.
2. Search and select tracks.
3. Keep a visible selected count.
4. Cancel discards changes.
5. Done applies once and returns to the filter sheet.

## 16. State matrix

| Condition | Header | Body | Result bar |
|---|---|---|---|
| Active, successful | Current view, On | Available sections | Count and Review tracks |
| Paused | Current view, Paused | Editable with paused banner | Resume |
| Updating with prior result | Current view, On | Editable | Prior count and Updating |
| First update | Current view, On | Editable | Updating |
| Update failed, prior result exists | Current view, On | Draft retained | Previous result, Retry, Revert |
| Update failed, no prior result | Current view, On | Draft retained | Retry |
| No matches | Current view, On | Active criteria visible | Clear criteria |
| Grouped result | Current view, On | Categories and Map colors | Review tracks |
| Ungrouped result | Current view, On | No empty category or color sections | Review tracks |
| Many categories | Current view, On | Limited quick chips and Manage | Review tracks |
| Gradient result | Current view, On | Percentile bands and Manage | Review tracks |
| Filter definitions unavailable | Filter | Error state | Retry |

## 17. Responsive behavior

### Phones

- One field column.
- Minimum 44px touch targets.
- Full-width picker, category manager, SQL view, and track review screens.
- Up to five quick category chips.
- Keep Manage fixed at the end of the category row.
- The result sentence may wrap to two lines.
- Respect safe-area insets.
- Area drawing temporarily gives the map full interaction.

### Tablets and desktop

- Use two columns only for naturally paired fields.
- Keep long, geographic, and track-selection controls full width.
- Use a contained drawer or overlay for view selection.
- Up to eight quick category chips.
- Keep the main form at a readable maximum width.

The section order and interaction meaning stay the same at every width.

## 18. Accessibility

- Use native buttons, inputs, and checkboxes where possible.
- Give the main sheet and inner screens accessible names.
- Trap focus inside open inner screens.
- Make the underlying sheet content inert while an inner screen is open.
- Return focus to the control that opened the inner screen.
- Escape closes only the topmost screen.
- Use `aria-pressed` for toggle chips or native checkbox semantics in lists.
- Ensure selected and excluded states do not depend on color.
- Keep focused chips visible when the row scrolls.
- Support arrow keys, Home, and End if the quick-chip row uses roving focus.
- Announce final result changes through a polite live region.
- Avoid announcing every intermediate keystroke and loading transition.
- Provide text labels and keyboard controls for gradient bands.
- Respect reduced-motion preferences.
- Do not use long-press as an action.

## 19. Copy changes

| Current or earlier proposal | Revised |
|---|---|
| `Filter` / `Colors` / `SQL` tabs | Current view with Change and overflow menu |
| `Filters` / `Settings` | Removed |
| `Choose a filter` | `Choose a view` |
| `Parameters` | `Show tracks` and `View options` |
| `Base scope` / `Shared` / `Specific` | Removed from user-facing copy |
| Repeated `Optional` pills | Mark required fields instead |
| `Color palette` | `Palette` under `Map colors` |
| `Legend order` | `Order` under `Map colors` |
| `selected tracks` / `total matches` / `categories x/y` | One track count plus a separate category state |
| `Live map` | Removed |
| `applied to map, stats and trends` | One general explanation outside the result bar |
| `Off` | `Paused` |
| `Show all tracks` used as disable | Separate Pause and Reset actions |
| `Tap to review matching tracks` on every group | One `Review tracks` action |
| `Hidden here only — stats unchanged` | Map legend title `Map visibility` |
| `Exclude from stats` in map legend | Removed; use Categories |

## 20. UX validation

Validate the structure before implementation with a clickable prototype or static interactive mockup.

### Core tasks

1. Show hiking tracks from a date range when those controls are available.
2. Change from the default view to tracks by year.
3. Exclude one year across the map and statistics.
4. Hide one group on the map without changing statistics.
5. Review all matching tracks.
6. Pause, edit, and resume the filter.
7. Reset the filter and undo the reset.
8. Recover from no matches.
9. Recover from a failed live update.
10. Draw, edit, and cancel a geographic area.

### Success criteria

- Users can predict whether an action affects all of MTL Explorer or only the map.
- Users can change views without losing compatible criteria unexpectedly.
- Users can find Review tracks from grouped and ungrouped results.
- Users do not need SQL, inheritance, or grouping terminology.
- Users can recover from no results and errors without closing the tool.
- All tasks work with keyboard-only navigation.
- The phone flow does not depend on long-press or hidden horizontal scrolling.

## 21. Decisions in this revision

- Keep `Filter` as the tool name.
- Use `View` for the selected filter recipe.
- Remove top-level Filter, Colors, and SQL tabs.
- Open view selection through Change.
- Put SQL in the overflow menu.
- Do not show capability badges.
- Do not infer Built-in or Custom from existing metadata.
- Preserve compatible general criteria when changing views.
- Reset category selection when changing views.
- Keep Pause separate from Reset.
- Keep map visibility separate from global category inclusion.
- Do not add global exclusion actions to map-legend rows.
- Do not use long-press.
- Keep percentile bands for gradient selection.
- Always offer Review tracks after a successful result.
- Do not add universal Activity or Keyword controls in this UX-only plan.

## 22. UX handoff stages

### Stage 1: structure

- Produce phone and desktop wireframes.
- Cover active, paused, loading, error, no-result, grouped, and ungrouped states.
- Prototype view selection, category management, and track review.

### Stage 2: interaction details

- Finalize parameter disclosure rules.
- Finalize area drawing and track selection journeys.
- Finalize focus movement and keyboard behavior.
- Finalize result and recovery copy.

### Stage 3: validation

- Run the core tasks with the prototype.
- Record confusion between Categories and Map visibility.
- Revise before creating an implementation plan.

### Stage 4: implementation handoff

- Map the approved UX to current capabilities.
- List any desired behavior that requires separate backend work.
- Create implementation phases only after those boundaries are agreed.
