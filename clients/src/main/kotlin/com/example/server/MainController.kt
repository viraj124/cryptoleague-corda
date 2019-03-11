package com.example.server

import com.example.flow.TradeFlow
import com.example.flow.TradeFlow.Initiator
import com.example.mq.Producer
import com.example.state.TradeState
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
import javax.servlet.http.HttpServletRequest

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */
class TradingAppSerializationWhitelist : SerializationWhitelist {
    // Add classes like this.
    override val whitelist = listOf(Timestamp::class.java)
}
@RestController
@RequestMapping("/api/trading/") // The paths for GET and POST requests are relative to this base path.
class MainController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    /**
     * Returns the node's name.
     */
    @GetMapping(value = "me", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the network map service. These names can be used to look up identities using
     * the identity service.
     */
    @GetMapping(value = "peers", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all IOU states that exist in the node's vault.
     */
    @GetMapping(value = "trades", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getTrades() : ResponseEntity<List<StateAndRef<TradeState>>> {
        return ResponseEntity.ok(proxy.vaultQueryBy<TradeState>().states)
    }

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /spring/api/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */

    @PostMapping(value = "create-trade", produces = arrayOf("text/plain"), headers = arrayOf("Content-Type=application/x-www-form-urlencoded"))
    fun createIOU(request: HttpServletRequest): ResponseEntity<String> {
        val counter = request.getParameter("counterParty")
        val tradeStatus = request.getParameter("tradeStatus")
        val userId = request.getParameter("userId")
        val assetCode = request.getParameter("assetCode")
        val orderType = request.getParameter("orderType")
        val transactionAmount = request.getParameter("transactionAmount").toInt()
        val transactionFees = request.getParameter("transactionFees").toInt()
        val transactionUnits = request.getParameter("transactionUnits").toInt()
        val transactionId = request.getParameter("transactionId")
        if (counter == null) {
            throw IllegalArgumentException("Query parameter 'Counter partyName' missing or has wrong format")
        }
        if (transactionAmount <= 0 ) {
            throw IllegalArgumentException("Query parameter 'Transaction Amount' must be non-negative")
        }
        val partyX500NameCounter = CordaX500Name.parse(counter)
        val counterParty = proxy.wellKnownPartyFromX500Name(partyX500NameCounter) ?:
        throw IllegalArgumentException("Target string \"$counter\" doesn't match any nodes on the network.")

        val producer = Producer()
        return try {
            val signedTx = proxy.startTrackedFlowDynamic(TradeFlow.Initiator::class.java,tradeStatus,counterParty,userId,assetCode,orderType,transactionAmount,transactionFees,transactionUnits,transactionId).returnValue.getOrThrow()
            val mapper = JacksonSupport.createNonRpcMapper()
            val result = mapper.writeValueAsString(signedTx)
            producer.send(result)
            ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${signedTx.id} committed to ledger.\n")
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            producer.send(ex.toString())
            ResponseEntity.badRequest().body(ex.message!!)
        }
    }

    /**
     * Displays all IOU states that only this node has been involved in.
     */
    @GetMapping(value = "getTrade", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getTransactionDetails(@RequestParam("linearID") linearID: String): ResponseEntity<List<StateAndRef<TradeState>>>? {
        if (linearID == null) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Linear Id cannot be null.\n")
        }
        val idParts = linearID.split('_')
        val uuid = idParts[idParts.size - 1]
        val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(UniqueIdentifier.fromString(uuid)),status = Vault.StateStatus.ALL)
        return ResponseEntity.ok(proxy.vaultQueryBy<TradeState>(criteria=criteria).states)
    }

}
