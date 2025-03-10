import React, {useMemo} from "react";
import {Tooltip, TooltipContent, TooltipProvider, TooltipTrigger} from "@/components/ui/tooltip";
import {Bar, ComposedChart, ResponsiveContainer, XAxis, YAxis} from "recharts";

const WaterfallChart = ({events}) => {
    const chartData = useMemo(() => {
        if (!events?.length) return [];

        const filteredEvents = events.filter(event =>
            event.category.toUpperCase() !== 'WORKFLOW'
        );

        const sortedEvents = [...filteredEvents].sort((a, b) =>
            new Date(a.startTimestamp).getTime() - new Date(b.startTimestamp).getTime()
        );

        if (sortedEvents.length === 0) return [];

        const firstTimestamp = new Date(sortedEvents[0].startTimestamp).getTime();

        return sortedEvents.map((event, index) => {
            const start = new Date(event.startTimestamp).getTime();
            const startOffset = (start - firstTimestamp) / 1000;
            const duration = event.endTimestamp
                ? (new Date(event.endTimestamp).getTime() - start) / 1000
                : 0;

            return {
                index,
                name: event.functionName || event.category,
                displayName: event.functionName || `${event.category} Event`,
                category: event.category,
                start: startOffset,
                duration: duration,
                left: startOffset,
                value: duration || 1,
                isPoint: !event.endTimestamp
            };
        });
    }, [events]);

    const getEventColor = (category) => {
        const colors = {
            'ACTIVITY': '#00e6e6',    // Turquoise (frequent, easy on eyes)
            'SIGNAL': '#ff5cd6',      // Hot Pink (short signals)
            'AWAIT': '#c466fc',       // Bright Amethyst (medium duration)
            'SLEEP': '#2e95ff',       // Electric Sapphire (long duration, beautiful & bold)
        };
        return colors[category];
    };

    const CustomYAxisTick = ({x, y, payload}) => {
        const maxLength = 15;
        const text = payload.value;
        const displayText = text.length > maxLength ? `${text.substring(0, maxLength)}...` : text;

        return (
            <TooltipProvider delayDuration={0}>
                <Tooltip>
                    <TooltipTrigger asChild>
                        <g transform={`translate(${x},${y})`}>
                            <text
                                x={-6}
                                y={0}
                                dy={4}
                                textAnchor="end"
                                fill="#94a3b8"
                                fontSize="12px"
                                style={{cursor: 'default'}}
                            >
                                {displayText}
                            </text>
                        </g>
                    </TooltipTrigger>
                    {text.length > maxLength && (
                        <TooltipContent side="left">
                            <p>{text}</p>
                        </TooltipContent>
                    )}
                </Tooltip>
            </TooltipProvider>
        );
    };

    const CustomBar = (props) => {
        const {x, y, width, height, payload} = props;
        const color = getEventColor(payload.category);
        const actualX = x + (payload.left * (width / payload.value));

        const tooltipContent = (
            <TooltipContent>
                <div className="space-y-1">
                    <p className="font-medium">{payload.displayName}</p>
                    <p className="text-sm">Category: {payload.category}</p>
                    <p className="text-sm">Start: {payload.start.toFixed(2)}s</p>
                    {!payload.isPoint && (
                        <p className="text-sm">Duration: {payload.duration.toFixed(2)}s</p>
                    )}
                </div>
            </TooltipContent>
        );

        if (payload.isPoint) {
            return (
                <TooltipProvider delayDuration={0}>
                    <Tooltip>
                        <TooltipTrigger asChild>
                            <g transform={`translate(${actualX},0)`}>
                                <circle
                                    cx={0}
                                    cy={y + height / 2}
                                    r={5}
                                    fill={color}
                                    filter="url(#glow)"
                                    style={{cursor: 'pointer'}}
                                />
                            </g>
                        </TooltipTrigger>
                        {tooltipContent}
                    </Tooltip>
                </TooltipProvider>
            );
        }

        return (
            <TooltipProvider delayDuration={0}>
                <Tooltip>
                    <TooltipTrigger asChild>
                        <g transform={`translate(${actualX},0)`}>
                            <defs>
                                <linearGradient id={`grad-${payload.index}`} x1="0" y1="0" x2="0" y2="1">
                                    <stop offset="0%" stopColor={color} stopOpacity={0.9}/>
                                    <stop offset="100%" stopColor={color} stopOpacity={0.7}/>
                                </linearGradient>
                                <filter id="glow">
                                    <feGaussianBlur stdDeviation="1" result="glow"/>
                                    <feMerge>
                                        <feMergeNode in="glow"/>
                                        <feMergeNode in="glow"/>
                                        <feMergeNode in="SourceGraphic"/>
                                    </feMerge>
                                </filter>
                            </defs>
                            <rect
                                x={0}
                                y={y}
                                width={Math.max(width, 2)}
                                height={height}
                                rx={2}
                                ry={2}
                                fill={`url(#grad-${payload.index})`}
                                filter="url(#glow)"
                                style={{cursor: 'pointer'}}
                            />
                        </g>
                    </TooltipTrigger>
                    {tooltipContent}
                </Tooltip>
            </TooltipProvider>
        );
    };

    const maxEndTime = Math.max(...chartData.map(d => d.start + d.value));

    const formatXAxisTick = (value) => {
        return `${value}s`;
    };

    return (
        <div className="rounded-md border-4 bg-slate-950 p-6">
            <div className="h-[400px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart
                        data={chartData}
                        layout="vertical"
                        margin={{top: 20, right: 40, left: 40, bottom: 20}}
                    >
                        <XAxis
                            type="number"
                            domain={[0, maxEndTime]}
                            stroke="#94a3b8"
                            tick={{fill: '#94a3b8'}}
                            tickLine={{stroke: '#94a3b8'}}
                            tickFormatter={formatXAxisTick}
                        />
                        <YAxis
                            type="category"
                            dataKey="name"
                            stroke="#94a3b8"
                            tick={CustomYAxisTick}
                            width={120}
                        />
                        <Bar
                            dataKey="value"
                            barSize={20}
                            shape={<CustomBar/>}
                            isAnimationActive={false}
                        />
                    </ComposedChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
};

export default WaterfallChart;