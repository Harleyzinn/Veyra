param([int]$GpuPid)
$ErrorActionPreference='Stop'
try {
 $engines=Get-CimInstance -ClassName Win32_PerfFormattedData_GPUPerformanceCounters_GPUEngine -Filter "Name LIKE 'pid_$($GpuPid)_%'"
 $values=@($engines | ForEach-Object {[double]$_.UtilizationPercentage})
 @{available=($values.Count -gt 0);engineCount=$values.Count;maxEnginePercent=($values | Measure-Object -Maximum).Maximum}|ConvertTo-Json -Compress
} catch { @{available=$false;reason='GPU performance counters unavailable'}|ConvertTo-Json -Compress }
