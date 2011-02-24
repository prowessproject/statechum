-module(gen_server_wrapper).
-export([exec_call_trace/3]).

exec_call_trace(Module, [{init, InitArgs} | Trace], OpProc) ->
    %%io:format("Executing gen_server:start_link({local, mod_under_test}, ~p, ~p, []).~n", [Module, InitArgs]),
    {ok, Pid} = gen_server:start_link({local, mod_under_test}, Module, InitArgs, []),
    %%Module:init(InitArgs),
    OpProc ! {self(), output, {init, InitArgs}},
    ok = call_trace({mod_under_test, Pid}, Trace, OpProc),
    %%Module:terminate(stop, who_cares_state),
    gen_server:cast(mod_under_test, stop);
    %%OpProc ! {self(), output, stop};
exec_call_trace(_Module, [], _OpProc) ->
    ok;
exec_call_trace(_Module, _TraceNoInitArgs, _OpProc) ->
    erlang:error("Trace with no init!").

call_trace(_ModulePid, [], _OpProc) ->
    ok;
%% This will accept any Output but records it in the written trace
call_trace({Module, Pid}, [{call, T, '*'} | Trace], OpProc) ->
    OP = gen_server:call(Module, T, 500),
    OpProc ! {self(), output, {call, T, OP}},
    call_trace({Module, Pid}, Trace, OpProc);
%% This deliberately unifies the provided Output value with that presented. 
%% It will throw a bad match exception if the system doesnt provide the right output
call_trace({Module, Pid}, [{call, T, OP} | Trace], OpProc) ->
    OP = gen_server:call(Module, T, 500),
    OpProc ! {self(), output, {call, T, OP}},
    call_trace({Module, Pid}, Trace, OpProc);
call_trace({Module, Pid}, [{call, T} | Trace], OpProc) ->
    _OP = gen_server:call(Module, T, 500),
    OpProc ! {self(), output, {call, T}},
    call_trace({Module, Pid}, Trace, OpProc);
call_trace({Module, Pid}, [{info, T} | Trace],OpProc) ->
    Pid ! T,
    receive 
	Msg ->
	    _OP = Msg
    after 500 ->
	    _OP = {timeout, T}
    end,
    OpProc ! {self(), output, {info, T}},
    call_trace({Module, Pid}, Trace, OpProc);
call_trace({Module, Pid}, [{cast, T} | Trace], OpProc) ->
    _OP = gen_server:cast(Module, T),
    OpProc ! {self(), output, {cast, T}},
    call_trace({Module, Pid}, Trace, OpProc).



