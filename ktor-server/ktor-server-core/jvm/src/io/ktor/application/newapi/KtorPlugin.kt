/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.newapi

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.random.*
import kotlinx.coroutines.*

public typealias OnCallHandler = suspend CallExecution.(ApplicationCall) -> Unit

public interface OnCall {
    public operator fun invoke(block: suspend CallExecution.(ApplicationCall) -> Unit): Unit
    public fun monitoring(block: suspend CallExecution.(ApplicationCall) -> Unit): Unit
    public fun fallback(block: suspend CallExecution.(ApplicationCall) -> Unit): Unit
}

public interface OnReceive {
    public operator fun invoke(block: suspend ReceiveExecution.(ApplicationCall) -> Unit): Unit
    public fun before(block: suspend ReceiveExecution.(ApplicationCall) -> Unit): Unit
}

public interface OnResponse {
    public operator fun invoke(block: suspend ResponseExecution.(ApplicationCall) -> Unit): Unit
    public fun before(block: suspend ResponseExecution.(ApplicationCall) -> Unit): Unit
    public fun after(block: suspend ResponseExecution.(ApplicationCall) -> Unit): Unit
}

public interface PluginContext {
    public val onCall: OnCall
    public val onReceive: OnReceive
    public val onResponse: OnResponse
}

/**
 * Compatibility class. It describes how (with what new functionality) some particular phase should be intercepted.
 * It is a wrapper over pipeline.intercept(phase) { ... } and is needed to hide old Plugins API functionality
 * */
public data class Interception<T : Any>(
    val phase: PipelinePhase,
    val action: (Pipeline<T, ApplicationCall>) -> Unit
)

/**
 * Compatibility class. Interception class for Call phase
 * */
public typealias CallInterception = Interception<Unit>
/**
 * Compatibility class. Interception class for Receive phase
 * */
public typealias ReceiveInterception = Interception<ApplicationReceiveRequest>
/**
 * Compatibility class. Interception class for Send phase
 * */
public typealias ResponseInterception = Interception<Any>

/**
 * Compatibility class. Every plugin that needs to be compatible with `beforePlugin(...)` and `afterPlugin(...)`
 * needs to implement this class. It defines a list of interceptions (see [Interception]) for phases in different pipelines
 * that are being intercepted by the current plugin.
 *
 * It is needed in order to get first/last phase in each pipeline for the current plugin and create a new phase
 * that is strictly earlier/later in this pipeline than any interception of the current plugin.
 *
 * Note: any [KtorPlugin] instance automatically fills these fields and there is no need to implement them by hand.
 * But if you want to use old plugin API based on pipelines and phases and to make it available for `beforePlugin`/
 * `afterPlugin` methods of the new plugins API, please consider filling the phases your plugin defines by implementing
 * this interface [InterceptionsHolder].
 *
 * Please, use [defineInterceptions] method inside the first init block to simply define all the interceptions for your
 * feature. Also it is useful to always delegate to [DefaultInterceptionsHolder] for simplicity.
 * */
public interface InterceptionsHolder {
    /**
     * A name for the current plugin.
     * */
    public val name: String get() = this.javaClass.simpleName

    @Deprecated("Please, use defineInterceptions instead")
    public val fallbackInterceptions: MutableList<CallInterception>

    @Deprecated("Please, use defineInterceptions instead")
    public val callInterceptions: MutableList<CallInterception>

    @Deprecated("Please, use defineInterceptions instead")
    public val monitoringInterceptions: MutableList<CallInterception>

    @Deprecated("Please, use defineInterceptions instead")
    public val beforeReceiveInterceptions: MutableList<ReceiveInterception>

    @Deprecated("Please, use defineInterceptions instead")
    public val onReceiveInterceptions: MutableList<ReceiveInterception>

    @Deprecated("Please, use defineInterceptions instead")
    public val beforeResponseInterceptions: MutableList<ResponseInterception>

    @Deprecated("Please, use defineInterceptions instead")
    public val onResponseInterceptions: MutableList<ResponseInterception>

    @Deprecated("Please, use defineInterceptions instead")
    public val afterResponseInterceptions: MutableList<ResponseInterception>

    public fun newPhase(): PipelinePhase = PipelinePhase("${name}Phase${Random.nextInt()}")

    public fun defineInterceptions(build: InterceptionsBuilder.() -> Unit) {
        InterceptionsBuilder(this).build()
    }

    /**
     * Builder class that helps to define interceptions for a feature written in old API.
     * */
    public class InterceptionsBuilder(private val holder: InterceptionsHolder) {
        /**
         * Define all phases in [ApplicationCallPipeline] that happen before, after and on [ApplicationCallPipeline.Fallback]
         * */
        public fun fallback(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Fallback)): Unit =
            addPhases(holder.fallbackInterceptions, *phases)

        /**
         * Define all phases in [ApplicationCallPipeline] that happen before, after and on [ApplicationCallPipeline.Features]
         * */
        public fun call(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Features)): Unit =
            addPhases(holder.callInterceptions, *phases)

        /**
         * Define all phases in [ApplicationCallPipeline] that happen before, after and on [ApplicationCallPipeline.Monitoring]
         * */
        public fun monitoring(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Monitoring)): Unit =
            addPhases(holder.monitoringInterceptions, *phases)

        /**
         * Define all phases in [ApplicationReceivePipeline] that happen before, after and on [ApplicationReceivePipeline.Before]
         * */
        public fun beforeReceive(vararg phases: PipelinePhase = arrayOf(ApplicationReceivePipeline.Before)): Unit =
            addPhases(holder.beforeReceiveInterceptions, *phases)

        /**
         * Define all phases in [ApplicationReceivePipeline] that happen before, after and on [ApplicationReceivePipeline.Transform]
         * */
        public fun onReceive(vararg phases: PipelinePhase = arrayOf(ApplicationReceivePipeline.Transform)): Unit =
            addPhases(holder.onReceiveInterceptions, *phases)

        /**
         * Define all phases in [ApplicationSendPipeline] that happen before, after and on [ApplicationSendPipeline.Before]
         * */
        public fun beforeResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.Before)): Unit =
            addPhases(holder.beforeResponseInterceptions, *phases)

        /**
         * Define all phases in [ApplicationSendPipeline] that happen before, after and on [ApplicationSendPipeline.Transform]
         * */
        public fun onResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.Transform)): Unit =
            addPhases(holder.onResponseInterceptions, *phases)

        /**
         * Define all phases in [ApplicationSendPipeline] that happen before, after and on [ApplicationSendPipeline.After]
         * */
        public fun afterResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.After)): Unit =
            addPhases(holder.afterResponseInterceptions, *phases)

        private fun <T : Any> addPhases(target: MutableList<Interception<T>>, vararg phases: PipelinePhase) {
            phases.forEach { phase ->
                target.add(Interception(phase) {})
            }
        }
    }
}

/**
 * Empty implementation of [InterceptionsHolder] interface that can be used for simplicity.
 * */
public class DefaultInterceptionsHolder(override val name: String) : InterceptionsHolder {
    override val fallbackInterceptions: MutableList<CallInterception> = mutableListOf()
    override val callInterceptions: MutableList<CallInterception> = mutableListOf()
    override val monitoringInterceptions: MutableList<CallInterception> = mutableListOf()
    override val beforeReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val onReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val beforeResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val onResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val afterResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
}

/**
 * [Execution] holds everything that is bound to the current plugin execution. Instance of [Execution] can be
 * usually accessed from inside of any callback that defines feature behaviour (ex.: [KtorPlugin.onCall],
 * [KtorPlugin.onReceive], [KtorPlugin.onResponse], etc.).
 *
 * [Execution] also provides useful methods, variables and mechanisms that cen be used in multiple advanced scenarios.
 *
 * **Please note that every method of the  [Execution] class should be considered an
 * advanced functionality and requires a deep knowledge and understanding of the internals of Ktor.
 * Please, read about [Pipeline] and [PipelinePhase] first.**
 *
 * @param SubjectT is a type of subject for the current feature that is being processed. See [subject] for more information.
 *
 * */
public inline class Execution<SubjectT : Any>(private val context: PipelineContext<SubjectT, ApplicationCall>) {
    /**
     * Continues execution of the HTTP pipeline starting after the current plugin.
     * Current plugin's code stops being executed from the moment of calling this function.
     * */
    public suspend fun proceed(): SubjectT = context.proceed()

    /**
     * Continues execution of the HTTP pipeline starting after the current plugin.
     * At the same time, the [subject] of the execution will be changed.
     * Current plugin's code stops being executed from the moment of calling this function.
     * */
    public suspend fun proceedWith(subectT: SubjectT): SubjectT = context.proceedWith(subectT)

    /**
     * Finishes execution of the current plugin as well as the current pipeline (see [Pipeline] for more information).
     * */
    public fun finish(): Unit = context.finish()


    /**
     * A subject of the current execution. A subject can be any object of some allowed type that is currently being
     * processed.
     *
     * - For the stage of processing a call, there is no [subject] (i.e. [SubjectT] is [Unit])
     * - For the stage of sending a response, the [subject] is an object that is currently being sent via HTTP protocol. ([SubjectT] can be [Any] type)
     * - For the stage of receiving a request data, the [subject] is of type [ApplicationReceiveRequest]. Please, read [ApplicationReceiveRequest] javadoc for more information.
     * */
    public val subject: SubjectT get() = context.subject

    /**
     * A call that is being processed by the plugin in the current execution.
     * */
    public val call: ApplicationCall get() = context.call

    // Useful methods

    /**
     * See [ApplicationEnvironment] for more details.
     * */
    public val environment: ApplicationEnvironment get() = context.application.environment

    /**
     * Configuration of the curreny application.
     * */
    public val configuration: ApplicationConfig get() = environment.config

    /**
     * Port of the current application. Same as in config.
     * */
    public val port: Int get() = configuration.propertyOrNull("ktor.deployment.port")?.getString()?.toInt() ?: 8080

    /**
     * Host of the current application. Same as in config.
     * */
    public val host: String get() = configuration.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"

    /**
     * Sets a shutdown hook. This method is useful for closing resources allocated by the feature.
     * */
    public fun onShutdown(block: suspend () -> Unit) {
        GlobalScope.launch(context.coroutineContext) {
            block()
        }
    }
}

/**
 * [Execution] for the stage of processing a call.
 * */
public typealias CallExecution = Execution<Unit>

/**
 * [Execution] for the stage of receiving data in a call.
 * */
public typealias ReceiveExecution = Execution<ApplicationReceiveRequest>

/**
 * [Execution] for the stage of sending response to a call.
 * */
public typealias ResponseExecution = Execution<Any>

/**
 * A plugin for Ktor that embeds into the HTTP pipeline and extends functionality of Ktor framework.
 * */
public abstract class KtorPlugin<Configuration : Any> private constructor(
    public override val name: String
) : ApplicationFeature<ApplicationCallPipeline, Configuration, KtorPlugin<Configuration>>, PluginContext,
    InterceptionsHolder {

    protected var configurationValue: Configuration? = null

    public val plugin: Configuration = configurationValue!!

    override val key: AttributeKey<KtorPlugin<Configuration>> = AttributeKey(name)

    override val fallbackInterceptions: MutableList<CallInterception> = mutableListOf()
    override val callInterceptions: MutableList<CallInterception> = mutableListOf()
    override val monitoringInterceptions: MutableList<CallInterception> = mutableListOf()

    override val beforeReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val onReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()

    override val beforeResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val onResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val afterResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()

    private fun <T : Any> onDefaultPhase(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        block: suspend Execution<T>.(ApplicationCall) -> Unit
    ) {
        interceptions.add(Interception(
            phase,
            action = { pipeline ->
                pipeline.intercept(phase) { Execution(this).block(call) }
            }
        ))
    }

    /**
     * Callable object that defines how HTTP call handling should be modified by the current [KtorPlugin].
     * */
    public override val onCall: OnCall = object : OnCall {
        private val plugin = this@KtorPlugin

        /**
         * Define how processing an HTTP call should be modified by the current [KtorPlugin].
         * */
        override operator fun invoke(block: suspend CallExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.callInterceptions, ApplicationCallPipeline.Features, block)
        }

        /**
         * Defines actions to perform before the call was processed by any feature (including [Routing]).
         * It is useful for monitoring and logging (see [CallLogging] feature) to be executed before any "real stuff"
         * was performed with the call because other features can change it's content as well as add more resource usage etc.
         * while for logging and monitoring it is important to observe the pure (untouched) data.
         * */
        override fun monitoring(block: suspend CallExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.monitoringInterceptions, ApplicationCallPipeline.Monitoring, block)
        }

        /**
         * Defines what to do with the call in case request was not handled for some reason.
         * Usually, it is handy to use fallback { call -> ...} to set the response with some message or status code,
         * or to throw an exception.
         * */
        override fun fallback(block: suspend CallExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.fallbackInterceptions, ApplicationCallPipeline.Fallback, block)
        }
    }


    /**
     * Callable object that defines how receiving data from HTTP call should be modified by the current [KtorPlugin].
     * */
    public override val onReceive: OnReceive = object : OnReceive {
        private val plugin = this@KtorPlugin

        /**
         * Define how current [KtorPlugin] should transform data received from a client.
         * */
        override fun invoke(block: suspend ReceiveExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.onReceiveInterceptions, ApplicationReceivePipeline.Transform, block)
        }

        /**
         * Defines actions to perform before any transformations were made to the received content. It is useful for caching.
         * */
        override fun before(block: suspend ReceiveExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.beforeReceiveInterceptions, ApplicationReceivePipeline.Before, block)
        }
    }

    /**
     * Callable object that defines how sending data to a client within HTTP call should be modified by the current [KtorPlugin].
     * */
    public override val onResponse: OnResponse = object : OnResponse {
        private val plugin = this@KtorPlugin

        /**
         * Do transformations of the data. Example: you can write a custom serializer using this method.
         * (Note: it is handy to also use [Execution.proceedWith] for this scenario)
         * */
        override fun invoke(block: suspend ResponseExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.onResponseInterceptions, ApplicationSendPipeline.Transform, block)
        }

        /**
         * Allows to use the direct result of call processing (see [OnCall.invoke]) and prepare data before sending a response will be executed.
         * */
        override fun before(block: suspend ResponseExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.beforeResponseInterceptions, ApplicationSendPipeline.Before, block)
        }

        /**
         * Allows to calculate some statistics on the data that was already sent to a client, or to handle errors.
         * (See [Metrics], [CachingHeaders], [StatusPages] features as examples).
         * */
        override fun after(block: suspend ResponseExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.afterResponseInterceptions, ApplicationSendPipeline.After, block)
        }
    }

    public abstract class RelativePluginContext(private val otherPlugin: InterceptionsHolder) : PluginContext {
        protected fun <T : Any> sortedPhases(
            interceptions: List<Interception<T>>,
            pipeline: Pipeline<*, ApplicationCall>
        ): List<PipelinePhase> =
            interceptions
                .map { it.phase }
                .sortedBy {
                    if (!pipeline.items.contains(it)) {
                        throw PluginNotInstalledException(otherPlugin.name)
                    }

                    pipeline.items.indexOf(it)
                }

        public abstract fun selectPhase(phases: List<PipelinePhase>): PipelinePhase?

        public abstract fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        )

        private fun <T : Any> insertToPhaseRelatively(
            interceptions: MutableList<Interception<T>>,
            block: suspend Execution<T>.(ApplicationCall) -> Unit
        ) {
            val currentPhase = otherPlugin.newPhase()

            interceptions.add(
                Interception(
                    currentPhase,
                    action = { pipeline ->
                        val phases = sortedPhases(interceptions, pipeline)
                        selectPhase(phases)?.let { lastDependentPhase ->
                            insertPhase(pipeline, lastDependentPhase, currentPhase)
                        }
                        pipeline.intercept(currentPhase) {
                            Execution(this).block(call)
                        }
                    }
                )
            )
        }

        override val onCall: OnCall = object : OnCall {
            override operator fun invoke(block: suspend CallExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.callInterceptions, block)
            }

            override fun monitoring(block: suspend CallExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.monitoringInterceptions, block)
            }

            override fun fallback(block: suspend CallExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.fallbackInterceptions, block)
            }
        }

        override val onReceive: OnReceive = object : OnReceive {
            override operator fun invoke(block: suspend ReceiveExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.onReceiveInterceptions, block)
            }

            override fun before(block: suspend ReceiveExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.beforeReceiveInterceptions, block)
            }
        }

        override val onResponse: OnResponse = object : OnResponse {
            override operator fun invoke(block: suspend ResponseExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.onResponseInterceptions, block)
            }

            override fun before(block: suspend ResponseExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.beforeResponseInterceptions, block)
            }

            override fun after(block: suspend ResponseExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.afterResponseInterceptions, block)
            }
        }
    }

    public class AfterPluginContext(plugin: InterceptionsHolder) : RelativePluginContext(plugin) {
        override fun selectPhase(phases: List<PipelinePhase>): PipelinePhase? = phases.lastOrNull()

        override fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        ) {
            pipeline.insertPhaseAfter(relativePhase, newPhase)
        }

    }

    public class BeforePluginContext(plugin: InterceptionsHolder) : RelativePluginContext(plugin) {
        override fun selectPhase(phases: List<PipelinePhase>): PipelinePhase? = phases.firstOrNull()

        override fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        ) {
            pipeline.insertPhaseBefore(relativePhase, newPhase)
        }

    }

    /**
     * Execute some actions right after some other [plugin] was already executed.
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onCall], [onResponse], etc.) and each of these actions will be executed right after all actions defined
     * by the given [plugin] were already executed in the same stage.
     * */
    public fun afterPlugin(plugin: InterceptionsHolder, build: AfterPluginContext.() -> Unit): Unit =
        AfterPluginContext(plugin).build()


    /**
     * Execute some actions right before some other [plugin] was already executed.
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onCall], [onResponse], etc.) and each of these actions will be executed right before all actions defined
     * by the given [plugin] were already executed in the same stage.
     * */
    public fun beforePlugin(plugin: InterceptionsHolder, build: BeforePluginContext.() -> Unit): Unit =
        BeforePluginContext(plugin).build()

    public companion object {
        /**
         * A canonical way to create a [KtorPlugin].
         * */
        public fun <Configuration : Any> createPlugin(
            name: String,
            createConfiguration: (ApplicationCallPipeline) -> Configuration,
            body: KtorPlugin<Configuration>.() -> Unit
        ): KtorPlugin<Configuration> = object : KtorPlugin<Configuration>(name) {

            override fun install(
                pipeline: ApplicationCallPipeline,
                configure: Configuration.() -> Unit
            ): KtorPlugin<Configuration> {
                configurationValue = createConfiguration(pipeline)
                configurationValue!!.configure()

                this.apply(body)

                fallbackInterceptions.forEach {
                    it.action(pipeline)
                }

                callInterceptions.forEach {
                    it.action(pipeline)
                }

                monitoringInterceptions.forEach {
                    it.action(pipeline)
                }

                beforeReceiveInterceptions.forEach {
                    it.action(pipeline.receivePipeline)
                }

                onReceiveInterceptions.forEach {
                    it.action(pipeline.receivePipeline)
                }

                beforeResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
                }

                onResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
                }

                afterResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
                }

                return this
            }
        }
    }
}
