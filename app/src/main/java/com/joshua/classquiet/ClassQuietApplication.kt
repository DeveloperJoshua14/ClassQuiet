package com.joshua.classquiet

import android.app.Application
import com.joshua.classquiet.background.AlarmScheduler
import com.joshua.classquiet.background.BackgroundCoordinator
import com.joshua.classquiet.data.AppSettingsRepository
import com.joshua.classquiet.data.RuntimeStateStore
import com.joshua.classquiet.data.ScheduleRepository
import com.joshua.classquiet.dnd.DndController
import com.joshua.classquiet.location.ClassLocationManager
import com.joshua.classquiet.location.GeofenceRegistrar
import com.joshua.classquiet.location.PermissionMonitor
import com.joshua.classquiet.notification.ActiveModeNotifier

class ClassQuietApplication : Application() {
    lateinit var scheduleRepository: ScheduleRepository
        private set
    lateinit var runtimeState: RuntimeStateStore
        private set
    lateinit var appSettings: AppSettingsRepository
        private set
    lateinit var locationManager: ClassLocationManager
        private set
    lateinit var permissionMonitor: PermissionMonitor
        private set
    lateinit var dndController: DndController
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set
    lateinit var geofenceRegistrar: GeofenceRegistrar
        private set
    lateinit var activeModeNotifier: ActiveModeNotifier
        private set
    lateinit var coordinator: BackgroundCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        scheduleRepository = ScheduleRepository(this)
        runtimeState = RuntimeStateStore(this)
        appSettings = AppSettingsRepository(this)
        locationManager = ClassLocationManager(this)
        permissionMonitor = PermissionMonitor(this)
        dndController = DndController(this)
        alarmScheduler = AlarmScheduler(this)
        geofenceRegistrar = GeofenceRegistrar(this, runtimeState)
        activeModeNotifier = ActiveModeNotifier(this)
        coordinator = BackgroundCoordinator(
            context = this,
            schedules = scheduleRepository,
            runtime = runtimeState,
            locationManager = locationManager,
            dndController = dndController,
            alarmScheduler = alarmScheduler,
            geofenceRegistrar = geofenceRegistrar,
            settings = appSettings,
            notifier = activeModeNotifier,
        )

        coordinator.ensurePeriodicEvaluation()
        alarmScheduler.scheduleNext(scheduleRepository.current())
    }
}
