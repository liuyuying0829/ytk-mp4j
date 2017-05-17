/**
*
* Copyright (c) 2017 ytk-mp4j https://github.com/yuantiku
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:

* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.

* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

package com.fenbi.mp4j.check.checklong;

import com.fenbi.mp4j.check.ProcessCheck;
import com.fenbi.mp4j.comm.ProcessCommSlave;
import com.fenbi.mp4j.exception.Mp4jException;
import com.fenbi.mp4j.operand.Operands;
import com.fenbi.mp4j.operator.Operators;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author xialong
 */
public class ProcessReduceCheck extends ProcessCheck {

    public ProcessReduceCheck(ProcessCommSlave slave, String serverHostName, int serverHostPort, int arrSize, int objSize, int runTime, boolean compress) {
        super(slave, serverHostName, serverHostPort, arrSize, objSize, runTime, compress);
    }

    @Override
    public void check() throws Mp4jException {
        int rank = slave.getRank();
        int slaveNum = slave.getSlaveNum();
        boolean success = true;
        int rootRank = 0;
        long start;
        long []arr = new long[arrSize];

        for (int rt = 1; rt <= runTime; rt++) {
            info("run time:" + rt + "...");

            // long array
            info("begin to reduce long arr...");

            for (int i = 0; i < arrSize; i++) {
                arr[i] = 1;
            }
            start = System.currentTimeMillis();
            slave.reduceArray(arr, Operands.LONG_OPERAND(compress), Operators.Long.SUM, 0, arrSize, rootRank);
            info("reduce long arr takes:" + (System.currentTimeMillis() - start));

            if (rank == rootRank) {
                for (int i = 0; i < arrSize; i++) {
                    if (arr[i] != slaveNum) {
                        success = false;
                    }
                }
            }

            if (!success) {
                info("reduce long arr error", false);
                slave.close(1);
            }

            info("reduce long arr success!");
            if (arrSize < 500 && rank == rootRank) {
                info("reduce result:" + Arrays.toString(arr), false);
            }

            // map
            info("begin to reduce long map...");
            Map<String, Long> map = new HashMap<>(objSize);
            for (int i = 0; i < objSize; i++) {
                map.put(i + "", new Long(1));
            }
            map.put(-(rank + 1) + "", new Long(1));

            start = System.currentTimeMillis();
            Map<String, Long> retMap = slave.reduceMap(map, Operands.LONG_OPERAND(compress), Operators.Long.SUM, rootRank);
            info("reduce long map takes:" + (System.currentTimeMillis() - start));

            success = true;
            if (rank == rootRank) {
                for (int i = 0; i < objSize; i++) {
                    Long val = retMap.get(i + "");
                    if (val == null || val.intValue() != slaveNum) {
                        success = false;
                    }
                }

                for (int r = 0; r < slaveNum; r++) {
                    String key = -(r + 1) + "";
                    Long val = retMap.get(key);
                    if (val == null || val.intValue() != 1) {
                        success = false;
                    }
                }

                if (retMap.size() != objSize + slaveNum) {
                    success = false;
                }
            }

            if (!success) {
                info("reduce long map error:" + retMap);
                slave.close(1);
            }

            if (objSize < 500 && rank == rootRank) {
                info("reduce long map:" + retMap, false);
            }
            info("reduce long map success!");

            // single long
            long singleLong = slave.reduce(1L, Operands.LONG_OPERAND(compress), Operators.Long.SUM, rootRank);
            if (rank == rootRank && ((int)singleLong) != slaveNum) {
                info("reduce single long error:" + singleLong, false);
                slave.close(1);
            }
            info("reduce single long success!");
        }

    }
}
