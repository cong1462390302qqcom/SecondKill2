package com.cong;

import com.sun.org.apache.regexp.internal.RE;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

@RestController
public class skController {

    static String secKillScript = "local userid=KEYS[1];\r\n"
            + "local prodid=KEYS[2];\r\n"
            + "local qtkey='sk:'..prodid..\":qt\";\r\n"
            + "local usersKey='sk:'..prodid..\":user\";\r\n"
            + "local userExists=redis.call(\"sismember\",usersKey,userid);\r\n"
            + "if tonumber(userExists)==1 then \r\n"
            + "   return 2;\r\n"
            + "end\r\n"
            + "local num= redis.call(\"get\" ,qtkey);\r\n"
            + "if tonumber(num)<=0 then \r\n"
            + "   return 0;\r\n"
            + "else \r\n"
            + "   redis.call(\"decr\",qtkey);\r\n"
            + "   redis.call(\"sadd\",usersKey,userid);\r\n"
            + "end\r\n"
            + "return 1";

    @PostMapping(value = "/sk/doSecondKill",produces = "text/html;charset=UTF-8")
    public String doSecondKillByLua(Integer id){
        Integer userid = (int)(Math.random()*10000);
//        Jedis jedis = new Jedis("192.168.223.130", 6379);
        //使用
        Jedis jedis = JedisPoolUtil.getJedisPoolInstance().getResource();
        //加载lua脚本
        String shal = jedis.scriptLoad(secKillScript);

        //传参数,执行
        Object obj = jedis.evalsha(shal, 2, userid + "", id + "");

        jedis.close();
        int result = (int)((long)obj);
        if(result == 1){
            System.out.println("秒杀成功"+userid);
        }else if(result==2){
            System.out.println(userid+"重复秒杀");
            return userid+"重复秒杀";
        }else{
            System.out.println("库存不足");
            return "库存不足";
        }


        return "ok";
    }

    public String doSecondKill(Integer id){

        Integer userid = (int)(Math.random()*10000);

        Integer pid = id;

        String qtkey = "sk:"+pid+":qt";
        String userKey = "sk:"+pid+":user";
        Jedis jedis = new Jedis("192.168.223.130",6379);

        //判断用户是否已经参加过
        Boolean sismember = jedis.sismember(userKey, userid+"");

        if(sismember){
            System.out.println("用户"+userid+"已经参加过活动");
            return "用户"+userid+"已经参加过活动";
        }

        //判断库存是否足够
        jedis.watch(qtkey);//启用事务，避免出现超卖

        String qtStr = jedis.get(qtkey);
        if(StringUtils.isEmpty(qtStr)){
            System.err.println("活动尚未开始");
            return "活动尚未开始";
        }

        //库存墙砖
        int qtNum = Integer.parseInt(qtStr);
        if(qtNum<=0){
            System.err.println("库存不足");
            return "库存不足";
        }

        Transaction multi = jedis.multi();//开启组队
        multi.decr( qtkey);
        //参加活动的用户存入redis
        multi.sadd(userKey,userid+"");
        System.out.println("用户"+userid+"成功参加过活动");
        multi.exec();


        jedis.close();
        return "ok";
    }
}
