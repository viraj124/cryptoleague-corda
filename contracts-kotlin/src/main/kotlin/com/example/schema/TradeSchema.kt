package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for TradeState.
 */
object TradeSchema

/**
 * An TradeState schema.
 */
object TradeSchemaV1 : MappedSchema(
        schemaFamily = TradeSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTrade::class.java)) {
    @Entity
    @Table(name = "trade_states_schema")
    class PersistentTrade(
            @Column(name = "initiatingParty")
            var initiatingPartyName: String,

            @Column(name = "counterParty")
            var counterParty: String,

            @Column(name = "tradeStatus")
            var tradeStatus: String,

            @Column(name = "userId")
            var userId: String,

            @Column(name = "assetCode")
            var assetCode: String,

            @Column(name = "orderType")
            var orderType: String,

            @Column(name = "transactionAmount")
            var transactionAmount: Double,

            @Column(name = "transactionFees")
            var transactionFees: Double,

            @Column(name = "transactionUnits")
            var transactionUnits: Double,

            @Column(name = "transactionId")
            var transactionId: String,

            @Column(name = "transactionDate")
            var transactionDate: Date,

            @Column(name = "transactionPrice")
            var transactionPrice: Double,

            @Column(name = "transactionTime")
            var transactionTime: LocalDateTime,

            @Column(name = "linear_id")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
    constructor() : this("","","","","","",0.0,0.0,0.0,"", java.util.Date(System.currentTimeMillis()),0.0,LocalDateTime.now(),UUID.randomUUID())
    }
}