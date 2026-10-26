package com.tradetrail.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradetrail.application.integration.CommandService;
import com.tradetrail.domain.TradeFailure;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
public class IdempotencyFilter extends OncePerRequestFilter {
    private final CommandService commands;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    public IdempotencyFilter(CommandService commands,PlatformTransactionManager manager,ObjectMapper json){this.commands=commands;this.transactions=new TransactionTemplate(manager);this.json=json;}
    protected boolean shouldNotFilter(HttpServletRequest request){
        String path=request.getRequestURI();
        return List.of("GET","HEAD","OPTIONS").contains(request.getMethod())||
            !(path.startsWith("/orders")||path.startsWith("/cart")||path.startsWith("/admin")||path.startsWith("/mock/checkout")||path.startsWith("/after-sales"));
    }
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        String key=request.getHeader("Idempotency-Key");
        if(key==null||key.isBlank()||key.length()>100){writeError(response,new TradeFailure("INVALID_REQUEST","写请求必须提供不超过 100 字符的 Idempotency-Key"));return;}
        byte[] bytes=request.getInputStream().readAllBytes();
        var replay=new HttpServletRequestWrapper(request){
            public ServletInputStream getInputStream(){return new ServletInputStream(){
                private final ByteArrayInputStream input=new ByteArrayInputStream(bytes);
                public int read(){return input.read();}
                public boolean isFinished(){return input.available()==0;}
                public boolean isReady(){return true;}
                public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
            };}
            public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        };
        var cached=new ContentCachingResponseWrapper(response);
        String command="http:"+request.getUserPrincipal().getName()+":"+key;
        try{
            transactions.executeWithoutResult(tx->{
                var prior=commands.lock(command,request.getMethod()+" "+request.getRequestURI()+" "+new String(bytes,StandardCharsets.UTF_8));
                if(prior.responseStatus()!=null){
                    cached.setStatus(prior.responseStatus());cached.setContentType("application/json;charset=UTF-8");
                    try{cached.getWriter().write(prior.responseBody());}catch(IOException e){throw new UncheckedIOException(e);}
                    return;
                }
                try{chain.doFilter(replay,cached);}catch(IOException e){throw new UncheckedIOException(e);}catch(ServletException e){throw new IllegalStateException(e);}
                if(cached.getStatus()>=400){tx.setRollbackOnly();return;}
                commands.complete(command,cached.getStatus(),new String(cached.getContentAsByteArray(),StandardCharsets.UTF_8));
            });
            cached.copyBodyToResponse();
        }catch(TradeFailure error){cached.resetBuffer();writeError(response,error);}
    }
    private void writeError(HttpServletResponse response,TradeFailure error)throws IOException{
        response.setStatus(error.code().equals("IDEMPOTENCY_CONFLICT")?409:400);response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(),ApiErrorHandler.body(error.code(),error.getMessage(),"",List.of()));
    }
}
