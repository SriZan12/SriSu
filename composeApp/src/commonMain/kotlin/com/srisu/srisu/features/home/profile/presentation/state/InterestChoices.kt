package com.srisu.srisu.features.home.profile.presentation.state

import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.User

internal data class InterestChoice(val id: Int, val label: String, val category: String, val aliases: Set<Int>)
private fun String?.displayLabel() = this.orEmpty().trim().split(Regex("\\s+")).joinToString(" ")

internal fun interestChoices(catalogue: List<InterestResponse.Interest?>, current: List<User.UserInterest?>): List<InterestChoice> {
    val rows = catalogue.filterNotNull().filter { (it.id ?: 0) > 0 && it.name.displayLabel().isNotEmpty() }
        .distinctBy { it.id }.map {
            val category = it.category?.label.displayLabel().ifEmpty {
                it.category?.name.displayLabel().lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
            }.ifEmpty { "Other interests" }
            InterestChoice(it.id!!, it.name.displayLabel(), category, setOf(it.id))
        }.toMutableList()
    current.filterNotNull().filter { (it.interest ?: 0) > 0 && it.removed != true && it.name.displayLabel().isNotEmpty() }
        .forEach { saved -> if (rows.none { saved.interest in it.aliases })
            rows += InterestChoice(saved.interest!!, saved.name.displayLabel(), "Your saved interests", setOf(saved.interest)) }
    return rows.groupBy { it.label.lowercase() }.values.map { aliases ->
        val first = aliases.minBy { it.id }
        first.copy(aliases = aliases.flatMap { it.aliases }.toSet())
    }.sortedWith(compareBy({ it.category.lowercase() }, { it.label.lowercase() }, { it.id }))
}

internal fun applyInterestSelection(current: List<User.UserInterest?>, choices: List<InterestChoice>, selected: Set<Int>): List<User.UserInterest> {
    val old = current.filterNotNull()
    // Name-only legacy interests have no catalogue ID; leave them untouched.
    return old.map { if (it.interest == null) it else it.copy(removed = it.interest !in selected) } +
        choices.filter { it.id in selected && old.none { saved -> saved.interest == it.id } }
            .map { User.UserInterest(interest = it.id, name = it.label, removed = false) }
}
