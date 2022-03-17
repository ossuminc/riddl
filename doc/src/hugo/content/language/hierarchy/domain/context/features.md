---
title: "Features" 
type: "page"
weight: 40
draft: "false"
---
A Feature is a requirement on a context. Features are specified very similarly to the
[Gherkin language](https://cucumber.io/docs/gherkin/reference/). The same keywords are used and the
style is more like RIDDL syntax. If you're already familiar with Gherkin then an example should
suffice:
```riddl
Feature "Guess the word" is {

  // The first example has two steps
  Scenario "Maker starts a game" is {
    When the Maker starts a game
    Then the Maker waits for a Breaker to join
  }

  // The second example has three steps
  Scenario "Breaker joins a game" is {
    Given the Maker has started a game with a word 
    When the Breaker joins the Maker's game
    Then the Breaker must guess a word knowing the length of the Maker's word choice.
  }
  
  Scenario "Breaker makes a guess" is {
    Given the Maker and Breaker have joined the same game
    When the Breaker makes a guess
    And the guess is correct
    Then the Breaker wins the game 
    And the game is over
    Else the Breaker may guess again
} described by {
  |The word guess game is a turn-based game for two players.
  |The Maker makes a word for the Breaker to guess. The game
  |is over when the Breaker guesses the Maker's word.
}
```

