package ssafy.runner.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.runner.domain.dto.order.OrderResponseDto;
import ssafy.runner.domain.dto.order.OrderUpdateRequestDto;
import ssafy.runner.domain.entity.Orders;
import ssafy.runner.domain.entity.Partner;
import ssafy.runner.domain.entity.Shop;
import ssafy.runner.domain.enums.OrderStatus;
import ssafy.runner.domain.repository.OrderRepository;
import ssafy.runner.domain.repository.PartnerRepository;
import ssafy.runner.domain.repository.ShopRepository;
import ssafy.runner.firebase.FirebaseCloudMessageService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PartnerOrderService {

    private final OrderRepository orderRepository;
    private final PartnerRepository partnerRepository;
    private final ShopRepository shopRepository;
    private final FirebaseCloudMessageService firebaseCloudMessageService;

    // 해당 샵의 전체 주문 내역 조회
    public List<OrderResponseDto> findByShop(String email) {

        Partner partner = partnerRepository.findByEmailWithShop(email)
                .orElseThrow(NoSuchElementException::new);
        Shop shop = partner.getShop();
        // 해당 샵의 주문 리스트 뽑기
        List<Orders> orderList = orderRepository.findByShop(shop);
        // 응답할 디티오 리스트에 담기
        List<OrderResponseDto> orderDtoList = new ArrayList<>();
        for (Orders order : orderList) {
            orderDtoList.add(new OrderResponseDto(order));
        }
        return orderDtoList;
    }

    // 해당 샵의 특정시간 이후 주문 내역 조회
    public List<OrderResponseDto> findByShopAndDay(String email, LocalDateTime dateTime) {

        Partner partner = partnerRepository.findByEmailWithShop(email)
                .orElseThrow(NoSuchElementException::new);
        Shop shop = partner.getShop();
        LocalDateTime todayStart = startDateTime(dateTime);
        List<Orders> orderList = orderRepository.findByShopAndDateAfter(shop, todayStart);
        List<OrderResponseDto> orderDtoList = new ArrayList<>();
        for (Orders order : orderList) {
            orderDtoList.add(new OrderResponseDto(order));
        }
        return orderDtoList;
    }

    // 해당 샵의 특정시간 이후 주문 내역 상태별 조회
    public List<OrderResponseDto> findByShopAndDayAndStatus(String email, LocalDateTime dateTime, String status) {

        Partner partner = partnerRepository.findByEmailWithShop(email)
                .orElseThrow(NoSuchElementException::new);
        Shop shop = partner.getShop();
        LocalDateTime todayStart = startDateTime(dateTime);
        OrderStatus orderStatus = OrderStatus.valueOf(status);
        List<Orders> orderList = orderRepository.findByShopAndDateAfterAndStatus(shop, todayStart, orderStatus);
        List<OrderResponseDto> orderDtoList = new ArrayList<>();
        for (Orders order : orderList) {
            orderDtoList.add(new OrderResponseDto(order));
        }
        return orderDtoList;
    }

    @Transactional
    public OrderResponseDto modifyStatus(Long orderId, OrderUpdateRequestDto orderUpdateRequestDto) {

        Orders order = orderRepository.findOrderNShopById(orderId)
                .orElseThrow(NoSuchElementException::new);
        OrderStatus enumStatus = OrderStatus.from(orderUpdateRequestDto.getStatus());

        // firebase Token 가져오기
        Long shopId = order.getShop().getId();
        Shop shop = shopRepository.findShopNPartnerById(shopId).orElseThrow(NoSuchElementException::new);
        String firebaseToken = shop.getPartner().getFirebaseToken();

        if (enumStatus == OrderStatus.PREPARING){
            System.out.println("메뉴 수락했습니다.");
        } else if (enumStatus == OrderStatus.REJECT) {
            System.out.println("메뉴 거절했습니다.");
        } else if (enumStatus == OrderStatus.ORDERED) {
            System.out.println("메뉴 준비완료 했습니다.");
        } else if (enumStatus == OrderStatus.COMPLETED) {
            System.out.println("메뉴 픽업 완료했습니다.");
        }
//        firebaseCloudMessageService.sendMessageTo(Token, Title, body);

        order.modifyOrderStatus(enumStatus);  // 현재 이 코드가 메뉴 상태 변경
        return new OrderResponseDto(order);
    }

    public int calPeriodRevenue(String email, LocalDateTime from, LocalDateTime to) {

        Partner partner = partnerRepository.findByEmailWithShop(email)
                .orElseThrow(NoSuchElementException::new);
        Shop shop = partner.getShop();
        LocalDateTime start = startDateTime(from);
        LocalDateTime end = endDateTime(to);
        Integer totalPrices = orderRepository.findRevenueListByDays(shop, start, end, OrderStatus.COMPLETED);
        return totalPrices == null ? 0 : totalPrices;
    }

    public int calTotalRevenue(String email, LocalDateTime now) {
        Partner partner = partnerRepository.findByEmailWithShop(email)
                .orElseThrow(NoSuchElementException::new);
        Shop shop = partner.getShop();
        Integer totalPrices =  orderRepository.findTotalRevenue(shop, OrderStatus.COMPLETED);
        return totalPrices == null ? 0 : totalPrices;
    }

    private LocalDateTime startDateTime(LocalDateTime dateTime) {
        return LocalDateTime.of(dateTime.toLocalDate(), LocalTime.of(0,0,0));
    }

    private LocalDateTime endDateTime(LocalDateTime dateTime) {
        return LocalDateTime.of(dateTime.toLocalDate(), LocalTime.of(23,59,59));
    }
}
