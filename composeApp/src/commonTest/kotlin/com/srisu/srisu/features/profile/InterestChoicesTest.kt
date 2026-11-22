package com.srisu.srisu.features.profile

import com.srisu.srisu.features.auth.data.remote.response.InterestResponse.Interest
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.profile.presentation.state.*
import kotlin.test.*

class InterestChoicesTest {
    @Test fun friendlyLabelsAndAliasesKeepExistingIds() {
        val rows = listOf(Interest(id=7,name=" Coffee  tasting ",category=Interest.Category(name="FOOD",label="Food & drink")),
            Interest(id=9,name="coffee tasting"),Interest(id=11,name=" "),null)
        val current=listOf(User.UserInterest(id=1,interest=9,name="coffee tasting",removed=false))
        val choices=interestChoices(rows,current)
        assertEquals(1,choices.size)
        assertEquals("Coffee tasting",choices.single().label)
        assertEquals("Food & drink",choices.single().category)
        assertEquals(setOf(7,9),choices.single().aliases)
        val saved=applyInterestSelection(current,choices,setOf(9))
        assertEquals(9,saved.single().interest)
        assertFalse(saved.single().removed!!)
    }
    @Test fun missingCatalogueAndNameOnlyLegacyChoicesAreNotSilentlyDeleted() {
        val old=listOf(User.UserInterest(id=2,interest=42,name="Walking",removed=false),User.UserInterest(id=3,name="Our hobby",removed=false))
        val choices=interestChoices(emptyList(),old)
        assertEquals("Your saved interests",choices.single().category)
        assertTrue(applyInterestSelection(old,choices,setOf(42)).all { it.removed==false })
        val changed=applyInterestSelection(old,choices,emptySet())
        assertTrue(changed.first().removed!!)
        assertFalse(changed.last().removed!!)
    }
}
